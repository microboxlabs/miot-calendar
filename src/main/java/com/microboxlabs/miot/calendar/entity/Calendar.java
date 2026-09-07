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

    /**
     * Filter key naming the service type a calendar serves ({@code v},
     * {@code otr}, {@code ote}); scopes {@link #isDefault} alongside
     * {@link #FILTER_KEY_ORIGIN}. Absent means the calendar answers for every
     * type nothing else claims — which is what every calendar predating this
     * key does, and why they keep resolving exactly as before.
     */
    public static final String FILTER_KEY_SERVICE_TYPE = "serviceType";

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
     * {@link #filter}'s {@code origin} and {@code serviceType} — see
     * {@link #findDefault}.
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
        return filterValue(filter, FILTER_KEY_ORIGIN);
    }

    /**
     * The service type a default calendar answers for, or "" when it answers
     * for every type nothing else claims. Stored lower-cased (see
     * {@code CalendarService#sanitizeFilter}) so this and the unique index
     * agree on what counts as the same key.
     */
    public static String defaultServiceTypeKey(Map<String, String> filter) {
        return filterValue(filter, FILTER_KEY_SERVICE_TYPE);
    }

    private static String filterValue(Map<String, String> filter, String key) {
        if (filter == null) {
            return "";
        }
        String value = filter.get(key);
        return value == null ? "" : value.trim();
    }

    /** All calendars flagged default, active or not. */
    public static List<Calendar> findDefaults() {
        return list("isDefault", true);
    }

    /**
     * The active default calendar for an origin and service type, in
     * descending specificity:
     *
     * <ol>
     *   <li>the calendar naming both — SCL's {@code otr} calendar</li>
     *   <li>the calendar naming the origin and no type — SCL's catch-all,
     *       which is every default that predates service types</li>
     *   <li>the calendar naming the type and no origin — one {@code otr}
     *       calendar shared by every delegación</li>
     *   <li>the calendar naming neither — the catch-all of last resort</li>
     * </ol>
     *
     * <p>Rung 2 is why adding this key moves nothing: existing defaults carry
     * no {@code serviceType}, so a {@code v} service still lands on its
     * origin's calendar until someone creates one that names a type.
     *
     * <p>Null when no rung matches — a meaningful answer, not a lookup
     * failure: the caller has nowhere to book and should book nowhere.
     *
     * <p>Matched in Java rather than SQL because both keys live inside a JSONB
     * column and the candidate set is one row per (origin, type) pair.
     */
    public static Calendar findDefault(String origin, String serviceType) {
        String wantedOrigin = origin == null ? "" : origin.trim();
        String wantedType = serviceType == null ? "" : serviceType.trim().toLowerCase();

        Calendar exact = null;
        Calendar originOnly = null;
        Calendar typeOnly = null;
        Calendar catchAll = null;

        for (Calendar candidate : findDefaults()) {
            if (!Boolean.TRUE.equals(candidate.active)) {
                continue;
            }
            String key = defaultOriginKey(candidate.filter);
            String type = defaultServiceTypeKey(candidate.filter);
            boolean originMatches = !wantedOrigin.isEmpty() && key.equals(wantedOrigin);
            boolean typeMatches = !wantedType.isEmpty() && type.equals(wantedType);

            if (originMatches && typeMatches) {
                exact = candidate;
            } else if (originMatches && type.isEmpty()) {
                originOnly = candidate;
            } else if (key.isEmpty() && typeMatches) {
                typeOnly = candidate;
            } else if (key.isEmpty() && type.isEmpty()) {
                catchAll = candidate;
            }
        }

        if (exact != null) {
            return exact;
        }
        if (originOnly != null) {
            return originOnly;
        }
        return typeOnly != null ? typeOnly : catchAll;
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
