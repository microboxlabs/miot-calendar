package com.microboxlabs.miot.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
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
            createdAt = ZonedDateTime.now();
        }
        updatedAt = ZonedDateTime.now();
        
        // Denormalize slot data
        if (slot != null) {
            this.slotDate = slot.slotDate;
            this.slotHour = slot.slotHour;
            this.slotMinutes = slot.slotMinutes;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // Finder methods

    /**
     * Find bookings by calendar and date range
     */
    public static List<Booking> findByCalendarAndDateRange(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return list("calendar.id = ?1 and slotDate >= ?2 and slotDate <= ?3 order by slotDate, slotHour, slotMinutes",
                calendarId, startDate, endDate);
    }

    /**
     * Find bookings by date range (all calendars)
     */
    public static List<Booking> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return list("slotDate >= ?1 and slotDate <= ?2 order by slotDate, slotHour, slotMinutes",
                startDate, endDate);
    }

    /**
     * Find booking by resource ID
     */
    public static List<Booking> findByResourceId(String resourceId) {
        return list("resourceId", resourceId);
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
