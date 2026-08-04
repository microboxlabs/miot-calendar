package com.microboxlabs.miot.calendar.entity;

import com.microboxlabs.miot.calendar.model.BookingStatus;
import com.microboxlabs.miot.calendar.model.BookingSyncStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Booking Entity
 * Represents a resource booked to a slot
 */
@Entity
@Table(name = "cld_bookings", indexes = {
    @Index(name = "idx_cld_bookings_slot", columnList = "slot_id"),
    @Index(name = "idx_cld_bookings_date", columnList = "calendar_id, slot_date"),
    @Index(name = "idx_cld_bookings_resource", columnList = "resource_id"),
    @Index(name = "idx_cld_bookings_type", columnList = "resource_type")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_cld_bookings_slot_resource", columnNames = {"slot_id", "resource_id"})
})
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    public Slot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    public Calendar calendar;

    // Denormalized for faster queries
    @Column(name = "slot_date", nullable = false)
    public LocalDate slotDate;

    @Column(name = "slot_hour", nullable = false)
    public Integer slotHour;

    @Column(name = "slot_minutes", nullable = false)
    public Integer slotMinutes;

    // Generic resource reference
    @Column(name = "resource_id", nullable = false, length = 100)
    public String resourceId;

    @Column(name = "resource_type", length = 50)
    public String resourceType;

    @Column(name = "resource_label", length = 255)
    public String resourceLabel;

    // Flexible resource data as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_data", columnDefinition = "jsonb")
    public Map<String, Object> resourceData;

    // Lifecycle status, advanced forward by the workflow coordinator (CALSYNC)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public BookingStatus status = BookingStatus.PLANNED;

    // Synchronization state of the booking's current data with an external
    // downstream system — orthogonal to the lifecycle status; null =
    // untracked. Written by the integration layer that mirrors bookings
    // outward (PENDING when a sync is dispatched, CONFIRMED/REJECTED once
    // the external system acknowledges).
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    public BookingSyncStatus syncStatus;

    @Column(name = "sync_detail", length = 500)
    public String syncDetail;

    @Column(name = "sync_at")
    public ZonedDateTime syncAt;

    // Audit fields
    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(name = "created_by", length = 100)
    public String createdBy;

    @Column(name = "updated_at", nullable = false)
    public ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        }
        if (status == null) {
            status = BookingStatus.PLANNED;
        }
        updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
        
        // Denormalize slot data
        if (slot != null) {
            this.slotDate = slot.slotDate;
            this.slotHour = slot.slotHour;
            this.slotMinutes = slot.slotMinutes;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // Finder methods

    /**
     * Find bookings whose generic resource identifier contains the requested
     * text, optionally narrowed by the other list filters.
     *
     * <p>{@code resourceIdContains} is deliberately expressed in calendar-domain
     * language. Callers decide what their resource identifiers mean.
     */
    public static List<Booking> findByResourceIdContaining(
            UUID calendarId,
            LocalDate startDate,
            LocalDate endDate,
            BookingStatus status,
            String resourceIdContains) {
        var conditions = new ArrayList<String>();
        conditions.add("lower(resourceId) like :resourceIdPattern escape '!'");
        var parameters = new HashMap<String, Object>();
        parameters.put(
            "resourceIdPattern",
            "%" + escapeLikePattern(resourceIdContains.trim().toLowerCase(Locale.ROOT)) + "%");

        if (calendarId != null) {
            conditions.add("calendar.id = :calendarId");
            parameters.put("calendarId", calendarId);
        }
        if (startDate != null) {
            conditions.add("slotDate >= :startDate");
            parameters.put("startDate", startDate);
        }
        if (endDate != null) {
            conditions.add("slotDate <= :endDate");
            parameters.put("endDate", endDate);
        }
        if (status != null) {
            conditions.add("status = :status");
            parameters.put("status", status);
        }
        String predicates = String.join(" and ", conditions);
        return find(
            predicates + " order by slotDate, slotHour, slotMinutes", parameters).list();
    }

    private static String escapeLikePattern(String value) {
        return value
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }

    /**
     * Find bookings by calendar and date range
     */
    public static List<Booking> findByCalendarAndDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return list("calendar.id = ?1 and slotDate >= ?2 and slotDate <= ?3 order by slotDate, slotHour, slotMinutes",
                calendarId, startDate, endDate);
    }

    /**
     * Find bookings by calendar, date range and lifecycle status
     */
    public static List<Booking> findByCalendarDateRangeAndStatus(
            UUID calendarId, LocalDate startDate, LocalDate endDate, BookingStatus status) {
        return list("calendar.id = ?1 and slotDate >= ?2 and slotDate <= ?3 and status = ?4 order by slotDate, slotHour, slotMinutes",
                calendarId, startDate, endDate, status);
    }

    /**
     * Find bookings by date range (all calendars)
     */
    public static List<Booking> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return list("slotDate >= ?1 and slotDate <= ?2 order by slotDate, slotHour, slotMinutes",
                startDate, endDate);
    }

    /**
     * Find bookings by date range and lifecycle status (all calendars)
     */
    public static List<Booking> findByDateRangeAndStatus(
            LocalDate startDate, LocalDate endDate, BookingStatus status) {
        return list("slotDate >= ?1 and slotDate <= ?2 and status = ?3 order by slotDate, slotHour, slotMinutes",
                startDate, endDate, status);
    }

    /**
     * Find booking by resource ID
     */
    public static List<Booking> findByResourceId(String resourceId) {
        return list("resourceId", resourceId);
    }

    /**
     * Find bookings by resource ID scoped to one calendar
     */
    public static List<Booking> findByResourceIdAndCalendar(String resourceId, UUID calendarId) {
        return list("resourceId = ?1 and calendar.id = ?2", resourceId, calendarId);
    }

    /**
     * Find booking by slot and resource
     */
    public static Booking findBySlotAndResource(UUID slotId, String resourceId) {
        return find("slot.id = ?1 and resourceId = ?2", slotId, resourceId).firstResult();
    }

    /**
     * Check if resource is already booked on a specific date
     */
    public static boolean isResourceBookedOnDate(String resourceId, LocalDate date) {
        return count("resourceId = ?1 and slotDate = ?2", resourceId, date) > 0;
    }

    /**
     * Count bookings made in a given time window on a given date — used to enforce a MANUAL
     * window's total-capacity cap (the cap applies across all of the window's slots for the day).
     */
    public static long countByWindowAndDate(UUID timeWindowId, LocalDate date) {
        return count("slot.timeWindow.id = ?1 and slotDate = ?2", timeWindowId, date);
    }

    /**
     * Find bookings by resource type
     */
    public static List<Booking> findByResourceType(String resourceType) {
        return list("resourceType", resourceType);
    }

    /**
     * Find bookings by slot
     */
    public static List<Booking> findBySlotId(UUID slotId) {
        return list("slot.id", slotId);
    }
}
