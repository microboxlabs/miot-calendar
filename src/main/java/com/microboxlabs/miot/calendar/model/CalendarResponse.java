package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Calendar;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a calendar
 */
@Schema(description = "Calendar data returned by the API")
public record CalendarResponse(
    @Schema(required = true, description = "Unique identifier of the calendar", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Unique code identifying the calendar", examples = {"loading-dock-south"})
    String code,

    @Schema(required = true, description = "Display name of the calendar", examples = {"Loading Dock South"})
    String name,

    @Schema(description = "Detailed description of the calendar purpose")
    String description,

    @Schema(required = true, description = "IANA timezone identifier", examples = {"America/Santiago"})
    String timezone,

    @Schema(required = true, description = "Whether the calendar is active")
    Boolean active,

    @Schema(required = true, description = "Timestamp when the calendar was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, description = "Timestamp when the calendar was last updated", format = "date-time")
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
