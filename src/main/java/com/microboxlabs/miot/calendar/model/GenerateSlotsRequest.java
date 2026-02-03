package com.microboxlabs.miot.calendar.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to generate slots for a date range
 */
public record GenerateSlotsRequest(
    UUID calendarId,
    LocalDate startDate,
    LocalDate endDate
) {
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
