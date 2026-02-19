package com.microboxlabs.miot.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calendar Entity
 * Represents a booking calendar with its configuration
 */
@Entity
@Table(name = "cld_calendars", indexes = {
    @Index(name = "idx_cld_calendars_code", columnList = "code"),
    @Index(name = "idx_cld_calendars_active", columnList = "active")
})
public class Calendar extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    public UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    public String code;

    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    @Column(name = "timezone", length = 50)
    public String timezone = "America/Santiago";

    @Column(name = "active")
    public Boolean active = true;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<TimeWindow> timeWindows;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {})
    @JoinTable(
        name = "cld_calendar_group_members",
        joinColumns        = @JoinColumn(name = "calendar_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    public List<CalendarGroup> groups = new ArrayList<>();

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
     * Find calendar by code
     */
    public static Calendar findByCode(String code) {
        return find("code", code).firstResult();
    }

    /**
     * Find all active calendars
     */
    public static List<Calendar> findAllActive() {
        return list("active", true);
    }

    /**
     * Find calendar by ID
     */
    public static Calendar findById(UUID id) {
        return find("id", id).firstResult();
    }

    /**
     * Find all active calendars belonging to the given group code
     */
    public static List<Calendar> findByGroupCode(String groupCode) {
        return list(
            "SELECT c FROM Calendar c JOIN c.groups g WHERE g.code = ?1 AND c.active = true",
            groupCode
        );
    }
}
