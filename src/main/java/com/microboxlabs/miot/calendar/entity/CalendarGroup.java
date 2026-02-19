package com.microboxlabs.miot.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CalendarGroup Entity
 * Represents a flat group label (tag) for organizing calendars
 */
@Entity
@Table(name = "cld_calendar_groups", indexes = {
    @Index(name = "idx_cld_calendar_groups_code", columnList = "code"),
    @Index(name = "idx_cld_calendar_groups_active", columnList = "active")
})
public class CalendarGroup extends PanacheEntityBase {

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
     * Find group by code
     */
    public static CalendarGroup findByCode(String code) {
        return find("code", code).firstResult();
    }

    /**
     * Find all active groups
     */
    public static List<CalendarGroup> findAllActive() {
        return list("active", true);
    }
}
