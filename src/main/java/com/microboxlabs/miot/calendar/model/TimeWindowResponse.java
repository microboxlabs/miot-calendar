package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.TimeWindow;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a time window
 */
@Schema(description = "Time window data returned by the API")
public record TimeWindowResponse(
    @Schema(required = true, description = "Unique identifier of the time window", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Identifier of the calendar this time window belongs to", format = "uuid")
    UUID calendarId,

    @Schema(required = true, description = "Name of the time window", examples = {"Morning Loading Window"})
    String name,

    @Schema(required = true, description = "Start hour of the time window (inclusive)", minimum = "0", maximum = "23", examples = {"8"})
    Integer startHour,

    @Schema(required = true, description = "End hour of the time window (exclusive)", minimum = "0", maximum = "23", examples = {"12"})
    Integer endHour,

    @Schema(required = true, description = "Duration of each slot in minutes (admin-set when slotGenerationMode = MANUAL, derived from the capacity model otherwise)", minimum = "1", examples = {"10"})
    Integer slotDurationMinutes,

    @Schema(required = true, description = "Total number of services this window can handle across all slots", minimum = "1", examples = {"20"})
    Integer capacity,

    @Schema(required = true, description = "Comma-separated days of the week (1=Monday to 7=Sunday)", examples = {"1,2,3,4,5"})
    String daysOfWeek,

    @Schema(required = true, description = "Date from which this time window is valid", format = "date")
    LocalDate validFrom,

    @Schema(description = "Date until which this time window is valid (null means no end date)", format = "date")
    LocalDate validTo,

    @Schema(required = true, description = "Whether the time window is active")
    Boolean active,

    @Schema(description = "UI color token for displaying this time window (e.g., \"emerald\", \"amber\")", examples = {"emerald"})
    String color,

    @Schema(required = true, description = "Discriminator: WINDOW (bookable) or BLOCK (non-bookable)")
    TimeWindowKind kind,

    @Schema(required = true, description = "Slot generation mode: AUTO (slot duration derived from capacity/parallelism) or MANUAL (admin-set slot duration)")
    SlotGenerationMode slotGenerationMode,

    @Schema(required = true, description = "Total number of slots generated across the window (floor(windowMinutes / slotDurationMinutes) in MANUAL mode; for BLOCK windows this is 0)", minimum = "0", examples = {"24"})
    Integer totalSlots,

    @Schema(required = true, description = "How many of the generated slots are bookable (OPEN); the remainder, up to totalSlots, are OVERFLOW", minimum = "0", examples = {"20"})
    Integer bookableSlots,

    @Schema(required = true, description = "Timestamp when the time window was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, description = "Timestamp when the time window was last updated", format = "date-time")
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
            timeWindow.capacity,
            timeWindow.daysOfWeek,
            timeWindow.validFrom,
            timeWindow.validTo,
            timeWindow.active,
            timeWindow.color,
            timeWindow.kind,
            timeWindow.slotGenerationMode,
            timeWindow.totalSlots(),
            timeWindow.bookableSlots(),
            timeWindow.createdAt,
            timeWindow.updatedAt
        );
    }
}
