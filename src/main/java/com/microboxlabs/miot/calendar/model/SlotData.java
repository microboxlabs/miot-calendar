package com.microboxlabs.miot.calendar.model;

import java.time.LocalDate;

/**
 * Slot data for requests and responses
 */
public record SlotData(
    LocalDate date,
    Integer hour,
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
