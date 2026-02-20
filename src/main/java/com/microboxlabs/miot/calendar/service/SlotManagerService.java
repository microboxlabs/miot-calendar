package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.SlotManager;
import com.microboxlabs.miot.calendar.entity.SlotManagerRun;
import com.microboxlabs.miot.calendar.model.RunStatus;
import com.microboxlabs.miot.calendar.model.SlotManagerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD and state management for slot managers.
 * Each method is its own short transaction so the executor can chain
 * multiple calls without holding a long-lived transaction across
 * slot generation batches.
 */
@ApplicationScoped
public class SlotManagerService {

    private static final Logger LOG = Logger.getLogger(SlotManagerService.class);

    // ── Read ──────────────────────────────────────────────────────────────

    @Transactional
    @SuppressWarnings("java:S3252")
    public List<SlotManager> getAllManagers() {
        return SlotManager.listAll();
    }

    @Transactional
    public List<SlotManager> getActiveManagers() {
        return SlotManager.findAllActive();
    }

    @Transactional
    @SuppressWarnings("java:S3252")
    public Optional<SlotManager> getManagerById(UUID id) {
        return Optional.ofNullable(SlotManager.findById(id));
    }

    @Transactional
    public List<SlotManagerRun> getRunsByManagerId(UUID managerId, int limit) {
        return SlotManagerRun.findByManagerId(managerId, limit);
    }

    @Transactional
    public List<SlotManagerRun> getRecentRuns(int limit) {
        return SlotManagerRun.findRecent(limit);
    }

    /**
     * Soft-lock check: returns true if any RUNNING record started within the
     * last hour exists.  Combined with ConcurrentExecution.SKIP on the
     * scheduler this is sufficient for most deployments.
     * For true HA, add quarkus-quartz with store-type=jdbc-cmt.
     */
    @Transactional
    public boolean isRunning() {
        return SlotManagerRun.countRunning() > 0;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public SlotManager createManager(SlotManagerRequest request) {
        request.validate();

        Calendar calendar = Calendar.findById(request.calendarId());
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + request.calendarId());
        }
        if (SlotManager.findByCalendarId(request.calendarId()) != null) {
            throw new IllegalArgumentException(
                "Slot manager already exists for calendar: " + request.calendarId());
        }

        SlotManager manager = new SlotManager();
        manager.calendar = calendar;
        manager.active = request.active() == null || request.active();
        manager.daysInAdvance = request.daysInAdvance() != null ? request.daysInAdvance() : 30;
        manager.batchDays = request.batchDays() != null ? request.batchDays() : 7;
        manager.reprocessFrom = request.reprocessFrom();
        manager.reprocessTo = request.reprocessTo();
        manager.persist();

        LOG.infof("Created slot manager for calendar %s", calendar.code);
        return manager;
    }

    @Transactional
    @SuppressWarnings("java:S3252")
    public SlotManager updateManager(UUID id, SlotManagerRequest request) {
        request.validateUpdate();

        SlotManager manager = SlotManager.findById(id);
        if (manager == null) {
            throw new IllegalArgumentException("Slot manager not found: " + id);
        }

        if (request.active() != null)       manager.active = request.active();
        if (request.daysInAdvance() != null) manager.daysInAdvance = request.daysInAdvance();
        if (request.batchDays() != null)     manager.batchDays = request.batchDays();

        // Reprocess: update both fields together; passing both as null clears them
        if (request.reprocessFrom() != null || request.reprocessTo() != null) {
            manager.reprocessFrom = request.reprocessFrom();
            manager.reprocessTo   = request.reprocessTo();
        }

        LOG.infof("Updated slot manager %s for calendar %s", id, manager.calendar.code);
        return manager;
    }

    @Transactional
    @SuppressWarnings("java:S3252")
    public void deactivateManager(UUID id) {
        SlotManager manager = SlotManager.findById(id);
        if (manager == null) {
            throw new IllegalArgumentException("Slot manager not found: " + id);
        }
        manager.active = false;
        LOG.infof("Deactivated slot manager %s for calendar %s", id, manager.calendar.code);
    }

    // ── Auto-provision helpers (package-private, called by CalendarService) ──

    /**
     * Creates a default SlotManager for the given calendar.
     * Guards against duplicates via findByCalendarId.
     */
    @Transactional
    SlotManager createDefaultManager(Calendar calendar) {
        if (SlotManager.findByCalendarId(calendar.id) != null) {
            LOG.warnf("Slot manager already exists for calendar %s, skipping auto-provision", calendar.code);
            return SlotManager.findByCalendarId(calendar.id);
        }

        SlotManager manager = new SlotManager();
        manager.calendar = calendar;
        manager.active = true;
        manager.daysInAdvance = 30;
        manager.batchDays = 7;
        manager.persist();

        LOG.infof("Auto-provisioned slot manager for calendar %s", calendar.code);
        return manager;
    }

    /**
     * Deactivates the SlotManager for the given calendar, if one exists.
     * Returns silently if none exists (handles calendars created with autoSlotManager=false).
     */
    @Transactional
    void deactivateManagerByCalendarId(UUID calendarId) {
        SlotManager manager = SlotManager.findByCalendarId(calendarId);
        if (manager == null) {
            return;
        }
        manager.active = false;
        LOG.infof("Deactivated slot manager for calendar %s", manager.calendar.code);
    }

    // ── Run lifecycle ─────────────────────────────────────────────────────

    /**
     * Opens a RUNNING run record and marks the manager as RUNNING.
     * Must be called at the start of each manager execution.
     */
    @Transactional
    @SuppressWarnings("java:S3252")
    public SlotManagerRun beginRun(UUID managerId, String triggeredBy) {
        SlotManager manager = SlotManager.findById(managerId);

        SlotManagerRun run = new SlotManagerRun();
        run.manager = manager;
        run.triggeredBy = triggeredBy;
        run.startedAt = ZonedDateTime.now();
        run.status = RunStatus.RUNNING;
        run.persist();

        manager.lastRunAt = run.startedAt;
        manager.lastRunStatus = RunStatus.RUNNING;
        manager.lastRunError = null;

        return run;
    }

    /**
     * Immutable outcome of a slot manager run, passed to {@link #completeRun}.
     */
    public record RunOutcome(RunStatus status, int slotsCreated, int slotsSkipped,
                             LocalDate generatedFrom, LocalDate generatedThrough,
                             String errorMessage) {}

    /**
     * Closes a run record with the given outcome and updates manager state.
     */
    @Transactional
    @SuppressWarnings("java:S3252")
    public void completeRun(UUID runId, UUID managerId, RunOutcome outcome) {
        SlotManagerRun run = SlotManagerRun.findById(runId);
        run.finishedAt = ZonedDateTime.now();
        run.status = outcome.status();
        run.slotsCreated = outcome.slotsCreated();
        run.slotsSkipped = outcome.slotsSkipped();
        run.generatedFrom = outcome.generatedFrom();
        run.generatedThrough = outcome.generatedThrough();
        run.errorMessage = outcome.errorMessage();

        SlotManager manager = SlotManager.findById(managerId);
        manager.lastRunStatus = outcome.status();
        manager.lastRunError = outcome.errorMessage();

        if (outcome.status() == RunStatus.SUCCESS && outcome.generatedThrough() != null) {
            // Advance the watermark and clear any pending reprocess window
            if (manager.generatedThrough == null || outcome.generatedThrough().isAfter(manager.generatedThrough)) {
                manager.generatedThrough = outcome.generatedThrough();
            }
            if (manager.reprocessFrom != null) {
                manager.reprocessFrom = null;
                manager.reprocessTo = null;
            }
        }
    }
}
