package com.microboxlabs.miot.calendar.entity;

import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TimeWindow Entity
 * Defines when slots are available and their constraints
 */
@Entity
@Table(name = "cld_time_windows", indexes = {
    @Index(name = "idx_cld_time_windows_calendar", columnList = "calendar_id, active")
})
public class TimeWindow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    public Calendar calendar;

    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @Column(name = "start_hour", nullable = false)
    public Integer startHour;

    @Column(name = "end_hour", nullable = false)
    public Integer endHour;

    @Column(name = "slot_duration_minutes", nullable = false)
    public Integer slotDurationMinutes = 30;

    @Column(name = "capacity", nullable = false)
    public Integer capacity = 1;

    @Column(name = "days_of_week", length = 50)
    public String daysOfWeek = "1,2,3,4,5";

    @Column(name = "valid_from", nullable = false)
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;

    @Column(name = "active")
    public Boolean active = true;

    @Column(name = "color", length = 32)
    public String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    public TimeWindowKind kind = TimeWindowKind.WINDOW;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    /**
     * Default slot step for BLOCK windows. Matches the planning grid cell size
     * so a block paints continuous CLOSED cells across the configured range.
     */
    public static final int BLOCK_SLOT_DURATION_MINUTES = 30;

    /**
     * Derive slot duration from the window duration and capacity model.
     * numberOfSlots = ceil(capacity / parallelism)
     * slotDuration  = windowMinutes / numberOfSlots (integer division, min 1)
     *
     * BLOCK windows have no meaningful capacity, so they use a fixed 30-min
     * step to align with the planning grid.
     */
    public int computeSlotDurationMinutes() {
        if (kind == TimeWindowKind.BLOCK) {
            return BLOCK_SLOT_DURATION_MINUTES;
        }
        int windowMinutes = (endHour - startHour) * 60;
        int numberOfSlots = computeNumberOfSlots();
        int duration = windowMinutes / numberOfSlots;
        return Math.max(duration, 1);
    }

    /**
     * Number of bookable slots needed to represent the full window capacity.
     */
    public int computeNumberOfSlots() {
        if (kind == TimeWindowKind.BLOCK) {
            return 0;
        }
        int parallelism = Math.max(calendar.parallelism, 1);
        return Math.max((capacity + parallelism - 1) / parallelism, 1);
    }

    /**
     * Capacity for a generated slot at the given zero-based position.
     * Full slots use calendar parallelism; the final slot may carry the remainder.
     */
    public int computeSlotCapacity(int slotIndex) {
        if (kind == TimeWindowKind.BLOCK) {
            return 0;
        }
        int parallelism = Math.max(calendar.parallelism, 1);
        int remaining = capacity - (slotIndex * parallelism);
        if (remaining <= 0) {
            return 0;
        }
        return Math.min(parallelism, remaining);
    }

    // Finder methods

    /**
     * Find active time windows for a calendar
     */
    public static List<TimeWindow> findActiveByCalendarId(UUID calendarId) {
        return list("calendar.id = ?1 and active = true", calendarId);
    }

    /**
     * Find time windows valid for a specific date
     */
    public static List<TimeWindow> findValidForDate(UUID calendarId, LocalDate date) {
        return list("calendar.id = ?1 and active = true and validFrom <= ?2 and (validTo is null or validTo >= ?2)",
                calendarId, date);
    }

    /**
     * Check if a day of week is included
     */
    public boolean includesDay(String dayOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return false;
        }
        for (String token : daysOfWeek.split(",")) {
            if (token.trim().equals(dayOfWeek)) {
                return true;
            }
        }
        return false;
    }
}
