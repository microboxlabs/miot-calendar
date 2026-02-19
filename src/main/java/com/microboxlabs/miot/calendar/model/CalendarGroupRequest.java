package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to create or update a calendar group
 */
@Schema(description = "Request payload to create or update a calendar group")
public record CalendarGroupRequest(
    @Schema(required = true, description = "Unique code identifying the group", examples = {"warehouse-south"}, maxLength = 50)
    String code,

    @Schema(required = true, description = "Display name of the group", examples = {"Warehouse South"}, maxLength = 255)
    String name,

    @Schema(description = "Detailed description of the group purpose", examples = {"Group for all calendars in Warehouse South"})
    String description,

    @Schema(description = "Whether the group is active", defaultValue = "true")
    Boolean active
) {
    /**
     * Validate the group request
     */
    public void validate() {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Group code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
    }
}
