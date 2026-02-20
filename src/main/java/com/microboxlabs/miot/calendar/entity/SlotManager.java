package com.microboxlabs.miot.calendar.entity;

import com.microboxlabs.miot.calendar.model.RunStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SlotManager Entity
 * Stores per-calendar automatic slot generation configuration.
 */
@Entity
@Table(name = "cld_slot_managers", indexes = {
    @Index(name = "idx_cld_slot_managers_calendar", columnList = "calendar_id"),
    @Index(name = "idx_cld_slot_managers_active",   columnList = "active")
})
public class SlotManager extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "calendar_id", nullable = false)
    public Calendar calendar;

    @Column(name = "active", nullable = false)
    public Boolean active = true;

    /** How many days ahead to keep slots generated */
    @Column(name = "days_in_advance", nullable = false)
    public Integer daysInAdvance = 30;

    /** Max days to generate per scheduler run batch */
    @Column(name = "batch_days", nullable = false)
    public Integer batchDays = 7;

    /** When set together with reprocessTo, forces regeneration of that date range on next run */
    @Column(name = "reprocess_from")
    public LocalDate reprocessFrom;

    @Column(name = "reprocess_to")
    public LocalDate reprocessTo;

    @Column(name = "last_run_at")
    public ZonedDateTime lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_status", length = 20)
    public RunStatus lastRunStatus;

    @Column(name = "last_run_error", columnDefinition = "TEXT")
    public String lastRunError;

    /** Latest date through which slots have been confirmed generated */
    @Column(name = "generated_through")
    public LocalDate generatedThrough;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // Finder methods

    public static SlotManager findByCalendarId(UUID calendarId) {
        return find("calendar.id", calendarId).firstResult();
    }

    public static List<SlotManager> findAllActive() {
        return list("active", true);
    }
}
