package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to generate slots for a date range
 */
@Schema(description = "Request payload to generate slots for a calendar within a date range")
public record GenerateSlotsRequest(
    @Schema(required = true, description = "Identifier of the calendar to generate slots for", format = "uuid")
    UUID calendarId,

    @Schema(required = true, description = "Start date of the generation range (inclusive)", format = "date", examples = {"2025-06-01"})
    LocalDate startDate,

    @Schema(required = true, description = "End date of the generation range (inclusive, max 90 days from start)", format = "date", examples = {"2025-06-30"})
    LocalDate endDate,

    @Schema(description = "If true, delete all unbooked slots in the range and regenerate them with the current time-window config — useful after a config change or to convert legacy OVERFLOW rows produced by an older deploy. Booked slots are preserved. Defaults to false (gaps only).", defaultValue = "false")
    Boolean reprocess
) {
    /** Absent in the payload ⇒ false. */
    public boolean reprocessOrDefault() {
        return Boolean.TRUE.equals(reprocess);
    }

    /**
     * Validate the generate slots request
     */
    public void validate() {
        if (calendarId == null) {
            throw new IllegalArgumentException("Calendar ID is required");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date is required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        // Limit to 90 days to prevent excessive generation
        if (startDate.plusDays(90).isBefore(endDate)) {
            throw new IllegalArgumentException("Date range cannot exceed 90 days");
        }
    }
}
