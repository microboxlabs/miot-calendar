package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request to create or update a calendar
 */
@Schema(description = "Request payload to create or update a calendar")
public record CalendarRequest(
    @Schema(required = true, description = "Unique code identifying the calendar", examples = {"loading-dock-south"}, maxLength = 100)
    String code,

    @Schema(required = true, description = "Display name of the calendar", examples = {"Loading Dock South"}, maxLength = 255)
    String name,

    @Schema(description = "Detailed description of the calendar purpose", examples = {"Scheduling calendar for Loading Dock South"})
    String description,

    @Schema(description = "IANA timezone identifier for the calendar", examples = {"America/Santiago"}, defaultValue = "UTC")
    String timezone,

    @Schema(description = "Whether the calendar is active and accepting bookings", defaultValue = "true")
    Boolean active,

    @Schema(description = "List of group codes to assign. null = no change; [] = remove all; [\"code1\"] = replace all")
    List<String> groups
) {
    /**
     * Validate the calendar request
     */
    public void validate() {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Calendar code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Calendar name is required");
        }
    }
}
