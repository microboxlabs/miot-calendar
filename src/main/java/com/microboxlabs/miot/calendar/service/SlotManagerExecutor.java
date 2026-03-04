package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.SlotManager;
import com.microboxlabs.miot.calendar.entity.SlotManagerRun;
import com.microboxlabs.miot.calendar.model.GenerateSlotsResponse;
import com.microboxlabs.miot.calendar.model.RunStatus;
import com.microboxlabs.miot.calendar.model.SlotManagerRunResponse;
import com.microboxlabs.miot.calendar.model.SlotManagerTriggerEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core slot manager execution logic.
 *
 * Deliberately NOT @Transactional at the class level: each calendar's
 * slot generation runs in its own transaction (delegated to
 * SlotGeneratorService), keeping individual transactions short even
 * when generating large date ranges.
 *
 * Transaction boundary summary:
 *   slotManagerService.isRunning()    → own short TX
 *   slotManagerService.beginRun()     → own short TX
 *   slotGeneratorService.generate()   → own TX per batch (inside generateSlots loop)
 *   slotManagerService.completeRun()  → own short TX
 */
@ApplicationScoped
public class SlotManagerExecutor {

    private static final Logger LOG = Logger.getLogger(SlotManagerExecutor.class);

    private final SlotGeneratorService slotGeneratorService;
    private final SlotManagerService slotManagerService;

    @Inject
    public SlotManagerExecutor(SlotGeneratorService slotGeneratorService, SlotManagerService slotManagerService) {
        this.slotGeneratorService = slotGeneratorService;
        this.slotManagerService = slotManagerService;
    }

    /**
     * Run all active managers.
     *
     * @param triggeredBy  SCHEDULER | API | CLI
     * @return list of run response records (one per manager that was attempted)
     */
    public List<SlotManagerRunResponse> runAll(String triggeredBy) {
        if (slotManagerService.isRunning()) {
            LOG.infof("Slot manager already running — skipping (triggered by: %s)", triggeredBy);
            return List.of();
        }

        List<UUID> ids = getActiveManagerIds();
        LOG.infof("Running slot managers for %d active calendar(s) (triggered by: %s)",
            ids.size(), triggeredBy);

        List<SlotManagerRunResponse> results = new ArrayList<>();
        for (UUID managerId : ids) {
            try {
                SlotManagerRunResponse run = runManager(managerId, triggeredBy);
                results.add(run);
            } catch (Exception e) {
                LOG.errorf(e, "Slot manager failed for manager %s", managerId);
            }
        }
        return results;
    }

    /**
     * Run a single manager by ID.
     *
     * @param managerId    manager to execute
     * @param triggeredBy  SCHEDULER | API | CLI
     * @return run response record
     */
    public SlotManagerRunResponse runManager(UUID managerId, String triggeredBy) {
        // Load manager snapshot inside its own transaction
        SlotManagerSnapshot snap = loadSnapshot(managerId);

        // Determine date range
        LocalDate from;
        LocalDate to;
        boolean isReprocess = snap.reprocessFrom() != null && snap.reprocessTo() != null;
        if (isReprocess) {
            from = snap.reprocessFrom();
            to   = snap.reprocessTo();
        } else {
            LocalDate horizon = LocalDate.now().plusDays(snap.daysInAdvance());
            from = snap.generatedThrough() != null
                ? snap.generatedThrough().plusDays(1)
                : LocalDate.now();
            to = horizon;
        }

        // Open run record
        SlotManagerRun runEntity = slotManagerService.beginRun(managerId, triggeredBy);
        UUID runId = runEntity.id;

        if (from.isAfter(to)) {
            slotManagerService.completeRun(runId, managerId, new SlotManagerService.RunOutcome(
                RunStatus.SKIPPED, 0, 0, null, null,
                "Already generated through " + snap.generatedThrough()));
            return loadRunResponse(runId);
        }

        // Generate in batches — each call is its own transaction
        int totalCreated = 0;
        int totalSkipped = 0;
        try {
            LocalDate batchStart = from;
            while (!batchStart.isAfter(to)) {
                LocalDate batchEnd = batchStart.plusDays(snap.batchDays() - 1L);
                if (batchEnd.isAfter(to)) batchEnd = to;

                GenerateSlotsResponse resp = slotGeneratorService.generateSlots(
                    snap.calendarId(), batchStart, batchEnd, isReprocess);
                totalCreated += resp.slotsCreated();
                totalSkipped += resp.slotsSkipped();

                batchStart = batchEnd.plusDays(1);
            }

            slotManagerService.completeRun(runId, managerId, new SlotManagerService.RunOutcome(
                RunStatus.SUCCESS, totalCreated, totalSkipped, from, to, null));

            LOG.infof("Slot manager SUCCESS [%s]: created=%d skipped=%d from=%s through=%s",
                snap.calendarCode(), totalCreated, totalSkipped, from, to);

        } catch (Exception e) {
            slotManagerService.completeRun(runId, managerId, new SlotManagerService.RunOutcome(
                RunStatus.FAILED, totalCreated, totalSkipped, from, null, e.getMessage()));
            LOG.errorf(e, "Slot manager FAILED [%s]", snap.calendarCode());
        }

        return loadRunResponse(runId);
    }

    /**
     * Runs a single slot manager after the originating write transaction commits.
     *
     * Fired by CalendarService when a SlotManager is auto-provisioned on calendar
     * creation, or when a time window is created/updated.  Using AFTER_SUCCESS
     * ensures the manager and time-window rows are visible to the generator's
     * queries before we start generating slots.
     *
     * REQUIRES_NEW is necessary because Narayana keeps the committed transaction
     * context associated with the thread during AFTER_SUCCESS notification.
     * Without it, the REQUIRED methods inside runManager() attempt to join the
     * already-inactive transaction and throw InactiveTransactionException.
     */
    @Transactional(TxType.REQUIRES_NEW)
    void onSlotManagerTrigger(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) SlotManagerTriggerEvent event) {
        LOG.infof("Slot manager trigger received for manager %s (triggered by: %s)",
            event.managerId(), event.triggeredBy());
        try {
            runManager(event.managerId(), event.triggeredBy());
        } catch (Exception e) {
            LOG.errorf(e, "Slot manager trigger failed for manager %s (triggered by: %s)",
                event.managerId(), event.triggeredBy());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Transactional
    List<UUID> getActiveManagerIds() {
        return SlotManager.findAllActive().stream()
            .map(m -> m.id)
            .toList();
    }

    @Transactional
    @SuppressWarnings("java:S3252")
    SlotManagerSnapshot loadSnapshot(UUID managerId) {
        SlotManager m = SlotManager.findById(managerId);
        if (m == null) throw new IllegalArgumentException("Slot manager not found: " + managerId);
        return new SlotManagerSnapshot(
            m.id, m.calendar.id, m.calendar.code,
            m.daysInAdvance, m.batchDays,
            m.reprocessFrom, m.reprocessTo,
            m.generatedThrough
        );
    }

    @Transactional
    @SuppressWarnings("java:S3252")
    SlotManagerRunResponse loadRunResponse(UUID runId) {
        SlotManagerRun run = SlotManagerRun.findById(runId);
        return SlotManagerRunResponse.from(run);
    }

    /** Immutable view of manager state captured before a run starts */
    record SlotManagerSnapshot(
        UUID id, UUID calendarId, String calendarCode,
        int daysInAdvance, int batchDays,
        LocalDate reprocessFrom, LocalDate reprocessTo,
        LocalDate generatedThrough
    ) {}
}
