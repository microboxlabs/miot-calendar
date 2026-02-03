package com.microboxlabs.miot.calendar.entity;

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

    @Column(name = "capacity_per_slot", nullable = false)
    public Integer capacityPerSlot = 1;

    @Column(name = "days_of_week", length = 50)
    public String daysOfWeek = "MON,TUE,WED,THU,FRI";

    @Column(name = "valid_from", nullable = false)
    public LocalDate validFrom;

    @Column(name = "valid_to")
    public LocalDate validTo;

    @Column(name = "active")
    public Boolean active = true;

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
        return daysOfWeek.contains(dayOfWeek);
    }
}
