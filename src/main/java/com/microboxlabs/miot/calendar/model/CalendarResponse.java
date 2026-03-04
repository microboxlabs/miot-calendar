package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Calendar;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.List;
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

    @Schema(required = true, description = "Number of parallel resources per slot", minimum = "1")
    Integer parallelism,

    @Schema(required = true, description = "Timestamp when the calendar was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, description = "Timestamp when the calendar was last updated", format = "date-time")
    ZonedDateTime updatedAt,

    @Schema(description = "Groups this calendar belongs to")
    List<CalendarGroupResponse> groups,

    @Schema(description = "Whether this calendar has a SlotManager provisioned")
    Boolean hasSlotManager
) {
    /**
     * Create from entity
     */
    public static CalendarResponse from(Calendar calendar, boolean hasSlotManager) {
        List<CalendarGroupResponse> groupResponses = calendar.groups != null
            ? calendar.groups.stream().map(CalendarGroupResponse::from).toList()
            : List.of();
        return new CalendarResponse(
            calendar.id,
            calendar.code,
            calendar.name,
            calendar.description,
            calendar.timezone,
            calendar.active,
            calendar.parallelism,
            calendar.createdAt,
            calendar.updatedAt,
            groupResponses,
            hasSlotManager
        );
    }
}
