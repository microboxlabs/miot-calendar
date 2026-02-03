package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.TimeWindow;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a time window
 */
public record TimeWindowResponse(
    UUID id,
    UUID calendarId,
    String name,
    Integer startHour,
    Integer endHour,
    Integer slotDurationMinutes,
    Integer capacityPerSlot,
    String daysOfWeek,
    LocalDate validFrom,
    LocalDate validTo,
    Boolean active,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    /**
     * Create from entity
     */
    public static TimeWindowResponse from(TimeWindow timeWindow) {
        return new TimeWindowResponse(
            timeWindow.id,
            timeWindow.calendar.id,
            timeWindow.name,
            timeWindow.startHour,
            timeWindow.endHour,
            timeWindow.slotDurationMinutes,
            timeWindow.capacityPerSlot,
            timeWindow.daysOfWeek,
            timeWindow.validFrom,
            timeWindow.validTo,
            timeWindow.active,
            timeWindow.createdAt,
            timeWindow.updatedAt
        );
    }
}
