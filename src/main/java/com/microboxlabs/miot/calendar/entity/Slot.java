package com.microboxlabs.miot.calendar.entity;

import com.microboxlabs.miot.calendar.model.SlotStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Slot Entity
 * Represents a bookable time slot
 */
@Entity
@Table(name = "cld_slots", indexes = {
    @Index(name = "idx_cld_slots_search", columnList = "calendar_id, slot_date, status"),
    @Index(name = "idx_cld_slots_availability", columnList = "slot_date, status, current_occupancy, capacity")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_cld_slots_calendar_datetime", 
                      columnNames = {"calendar_id", "slot_date", "slot_hour", "slot_minutes"})
})
public class Slot extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    public Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_window_id")
    public TimeWindow timeWindow;

    @Column(name = "slot_date", nullable = false)
    public LocalDate slotDate;

    @Column(name = "slot_hour", nullable = false)
    public Integer slotHour;

    @Column(name = "slot_minutes", nullable = false)
    public Integer slotMinutes;

    @Column(name = "capacity", nullable = false)
    public Integer capacity;

    @Column(name = "current_occupancy")
    public Integer currentOccupancy = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    public SlotStatus status = SlotStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Booking> bookings;

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
     * Find slots by calendar and date range
     */
    public static List<Slot> findByCalendarAndDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return list("calendar.id = ?1 and slotDate >= ?2 and slotDate <= ?3 order by slotDate, slotHour, slotMinutes",
                calendarId, startDate, endDate);
    }

    /**
     * Find available slots (OPEN, not individually full, and — for slots that belong to a time
     * window — whose window has not yet reached its total-capacity cap for that date). The window
     * cap matters for MANUAL windows, where the slot grid is intentionally larger than the
     * window's booking capacity; for AUTO windows the per-slot capacities already sum to the
     * window capacity, so the extra predicate is a no-op there.
     */
    public static List<Slot> findAvailableByCalendarAndDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return list("select s from Slot s left join s.timeWindow tw "
                + "where s.calendar.id = ?1 and s.slotDate >= ?2 and s.slotDate <= ?3 "
                + "and s.status = ?4 and s.currentOccupancy < s.capacity "
                + "and (tw is null or "
                + "  (select coalesce(sum(s2.currentOccupancy), 0) from Slot s2 "
                + "   where s2.timeWindow = tw and s2.slotDate = s.slotDate) < tw.capacity) "
                + "order by s.slotDate, s.slotHour, s.slotMinutes",
                calendarId, startDate, endDate, SlotStatus.OPEN);
    }

    /**
     * Find slot by calendar, date, hour, and minutes
     */
    public static Slot findByCalendarAndDateTime(UUID calendarId, LocalDate date, Integer hour, Integer minutes) {
        return find("calendar.id = ?1 and slotDate = ?2 and slotHour = ?3 and slotMinutes = ?4",
                calendarId, date, hour, minutes).firstResult();
    }

    /**
     * Delete all slots belonging to a calendar
     */
    public static long deleteByCalendarId(UUID calendarId) {
        return delete("calendar.id", calendarId);
    }

    /**
     * Delete unbooked slots for a calendar within a date range.
     * Only removes slots with currentOccupancy = 0 to preserve existing bookings.
     */
    public static long deleteUnbookedByCalendarAndDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return delete("calendar.id = ?1 and slotDate >= ?2 and slotDate <= ?3 and currentOccupancy = 0",
                calendarId, startDate, endDate);
    }

    /**
     * Check if slot has availability
     */
    public boolean hasAvailability() {
        return status == SlotStatus.OPEN && currentOccupancy < capacity;
    }

    /**
     * Increment occupancy
     */
    public void incrementOccupancy() {
        this.currentOccupancy++;
        if (this.currentOccupancy >= this.capacity) {
            this.status = SlotStatus.FULL;
        }
    }

    /**
     * Decrement occupancy
     */
    public void decrementOccupancy() {
        if (this.currentOccupancy > 0) {
            this.currentOccupancy--;
        }
        if (this.status == SlotStatus.FULL && this.currentOccupancy < this.capacity) {
            this.status = SlotStatus.OPEN;
        }
    }
}
