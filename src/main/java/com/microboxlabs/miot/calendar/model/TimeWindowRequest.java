package com.microboxlabs.miot.calendar.model;

import java.time.LocalDate;

/**
 * Request to create or update a time window
 */
public record TimeWindowRequest(
    String name,
    Integer startHour,
    Integer endHour,
    Integer slotDurationMinutes,
    Integer capacityPerSlot,
    String daysOfWeek,
    LocalDate validFrom,
    LocalDate validTo,
    Boolean active
) {
    /**
     * Validate the time window request
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Time window name is required");
        }
        if (startHour == null || startHour < 0 || startHour > 23) {
            throw new IllegalArgumentException("Start hour must be between 0 and 23");
        }
        if (endHour == null || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("End hour must be between 0 and 23");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("Valid from date is required");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("Valid to date must be after valid from date");
        }
    }
}
