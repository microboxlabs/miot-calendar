package com.microboxlabs.miot.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** Filter key naming the origin a calendar serves; scopes {@link #isDefault}. */
    public static final String FILTER_KEY_ORIGIN = "origin";

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

    @Column(name = "parallelism", nullable = false)
    public Integer parallelism = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter", columnDefinition = "jsonb")
    public Map<String, String> filter;

    /**
     * Whether this is the calendar to use when an integrating system must place
     * a booking but was given no calendar to place it in. Scoped by
     * {@link #filter}'s {@code origin} — see {@link #findDefaultForOrigin}.
     */
    @Column(name = "is_default", nullable = false)
    public Boolean isDefault = false;

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

    /** The key a default calendar answers for: its filter origin, or "" for none. */
    public static String defaultOriginKey(Map<String, String> filter) {
        if (filter == null) {
            return "";
        }
        String origin = filter.get(FILTER_KEY_ORIGIN);
        return origin == null ? "" : origin.trim();
    }

    /** All calendars flagged default, active or not. */
    public static List<Calendar> findDefaults() {
        return list("isDefault", true);
    }

    /**
     * The active default calendar for an origin: the one whose filter names
     * that origin, else the one with no origin filter, which answers for
     * everything not claimed specifically. Null when neither exists — a
     * meaningful answer, not a lookup failure: the caller has nowhere to book.
     *
     * <p>Matched in Java rather than SQL because the origin lives inside a
     * JSONB column and the candidate set is one row per origin.
     */
    public static Calendar findDefaultForOrigin(String origin) {
        String wanted = origin == null ? "" : origin.trim();
        Calendar fallback = null;
        for (Calendar candidate : findDefaults()) {
            if (!Boolean.TRUE.equals(candidate.active)) {
                continue;
            }
            String key = defaultOriginKey(candidate.filter);
            if (!wanted.isEmpty() && key.equals(wanted)) {
                return candidate;
            }
            if (key.isEmpty()) {
                fallback = candidate;
            }
        }
        return fallback;
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
