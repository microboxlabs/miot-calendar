package com.microboxlabs.miot.calendar.entity;

import com.microboxlabs.miot.calendar.model.RunStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SlotManagerRun Entity
 * Audit trail of each slot manager execution.
 */
@Entity
@Table(name = "cld_slot_manager_runs", indexes = {
    @Index(name = "idx_cld_slot_manager_runs_manager", columnList = "manager_id"),
    @Index(name = "idx_cld_slot_manager_runs_status",  columnList = "status, started_at")
})
public class SlotManagerRun extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    public SlotManager manager;

    /** What triggered this run: SCHEDULER, API, CLI */
    @Column(name = "triggered_by", nullable = false, length = 20)
    public String triggeredBy;

    @Column(name = "started_at", nullable = false)
    public ZonedDateTime startedAt;

    @Column(name = "finished_at")
    public ZonedDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public RunStatus status;

    @Column(name = "slots_created", nullable = false)
    public Integer slotsCreated = 0;

    @Column(name = "slots_skipped", nullable = false)
    public Integer slotsSkipped = 0;

    @Column(name = "generated_from")
    public LocalDate generatedFrom;

    @Column(name = "generated_through")
    public LocalDate generatedThrough;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    // Finder methods

    public static List<SlotManagerRun> findByManagerId(UUID managerId, int limit) {
        return find("manager.id = ?1", io.quarkus.panache.common.Sort.descending("startedAt"), managerId)
            .page(0, limit)
            .list();
    }

    public static List<SlotManagerRun> findRecent(int limit) {
        return findAll(io.quarkus.panache.common.Sort.descending("startedAt"))
            .page(0, limit)
            .list();
    }

    public static long countRunning() {
        return count("status = ?1 AND startedAt > ?2",
            RunStatus.RUNNING, ZonedDateTime.now().minusHours(1));
    }
}
