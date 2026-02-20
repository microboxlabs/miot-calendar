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
    public List<SlotManager> getAllManagers() {
        return SlotManager.listAll();
    }

    @Transactional
    public List<SlotManager> getActiveManagers() {
        return SlotManager.findAllActive();
    }

    @Transactional
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
        manager.active = request.active() != null ? request.active() : true;
        manager.daysInAdvance = request.daysInAdvance() != null ? request.daysInAdvance() : 30;
        manager.batchDays = request.batchDays() != null ? request.batchDays() : 7;
        manager.reprocessFrom = request.reprocessFrom();
        manager.reprocessTo = request.reprocessTo();
        manager.persist();

        LOG.infof("Created slot manager for calendar %s", calendar.code);
        return manager;
    }

    @Transactional
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
    public void deactivateManager(UUID id) {
        SlotManager manager = SlotManager.findById(id);
        if (manager == null) {
            throw new IllegalArgumentException("Slot manager not found: " + id);
        }
        manager.active = false;
        LOG.infof("Deactivated slot manager %s for calendar %s", id, manager.calendar.code);
    }

    // ── Run lifecycle ─────────────────────────────────────────────────────

    /**
     * Opens a RUNNING run record and marks the manager as RUNNING.
     * Must be called at the start of each manager execution.
     */
    @Transactional
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
     * Closes a run record with the given outcome and updates manager state.
     */
    @Transactional
    public void completeRun(UUID runId, UUID managerId, RunStatus status,
                            int slotsCreated, int slotsSkipped,
                            LocalDate generatedFrom, LocalDate generatedThrough,
                            String errorMessage) {

        SlotManagerRun run = SlotManagerRun.findById(runId);
        run.finishedAt = ZonedDateTime.now();
        run.status = status;
        run.slotsCreated = slotsCreated;
        run.slotsSkipped = slotsSkipped;
        run.generatedFrom = generatedFrom;
        run.generatedThrough = generatedThrough;
        run.errorMessage = errorMessage;

        SlotManager manager = SlotManager.findById(managerId);
        manager.lastRunStatus = status;
        manager.lastRunError = errorMessage;

        if (status == RunStatus.SUCCESS && generatedThrough != null) {
            // Advance the watermark and clear any pending reprocess window
            if (manager.generatedThrough == null || generatedThrough.isAfter(manager.generatedThrough)) {
                manager.generatedThrough = generatedThrough;
            }
            if (manager.reprocessFrom != null) {
                manager.reprocessFrom = null;
                manager.reprocessTo = null;
            }
        }
    }
}
