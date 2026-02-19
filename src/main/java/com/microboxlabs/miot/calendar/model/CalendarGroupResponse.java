package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.CalendarGroup;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a calendar group
 */
@Schema(description = "Calendar group data returned by the API")
public record CalendarGroupResponse(
    @Schema(required = true, description = "Unique identifier of the group", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Unique code identifying the group", examples = {"warehouse-south"})
    String code,

    @Schema(required = true, description = "Display name of the group", examples = {"Warehouse South"})
    String name,

    @Schema(description = "Detailed description of the group purpose")
    String description,

    @Schema(required = true, description = "Whether the group is active")
    Boolean active,

    @Schema(required = true, description = "Timestamp when the group was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, description = "Timestamp when the group was last updated", format = "date-time")
    ZonedDateTime updatedAt
) {
    /**
     * Create from entity
     */
    public static CalendarGroupResponse from(CalendarGroup group) {
        return new CalendarGroupResponse(
            group.id,
            group.code,
            group.name,
            group.description,
            group.active,
            group.createdAt,
            group.updatedAt
        );
    }
}
