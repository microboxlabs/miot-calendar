package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Slot data for requests and responses
 */
@Schema(description = "Date and time identifying a specific slot")
public record SlotData(
    @Schema(required = true, description = "Date of the slot", format = "date", examples = {"2025-06-15"})
    LocalDate date,

    @Schema(required = true, description = "Hour of the slot (0-23)", minimum = "0", maximum = "23", examples = {"10"})
    Integer hour,

    @Schema(required = true, description = "Minutes of the slot (0 or 30)", minimum = "0", maximum = "59", examples = {"30"})
    Integer minutes
) {
    /**
     * Validate slot data
     */
    public void validate() {
        if (date == null) {
            throw new IllegalArgumentException("Slot date is required");
        }
        if (hour == null || hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Slot hour must be between 0 and 23");
        }
        if (minutes == null || (minutes != 0 && minutes != 30)) {
            throw new IllegalArgumentException("Slot minutes must be 0 or 30");
        }
    }
}
