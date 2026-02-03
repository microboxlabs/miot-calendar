package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Calendar;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a calendar
 */
public record CalendarResponse(
    UUID id,
    String code,
    String name,
    String description,
    String timezone,
    Boolean active,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    /**
     * Create from entity
     */
    public static CalendarResponse from(Calendar calendar) {
        return new CalendarResponse(
            calendar.id,
            calendar.code,
            calendar.name,
            calendar.description,
            calendar.timezone,
            calendar.active,
            calendar.createdAt,
            calendar.updatedAt
        );
    }
}
