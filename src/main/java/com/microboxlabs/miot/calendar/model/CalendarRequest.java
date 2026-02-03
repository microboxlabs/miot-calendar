package com.microboxlabs.miot.calendar.model;

/**
 * Request to create or update a calendar
 */
public record CalendarRequest(
    String code,
    String name,
    String description,
    String timezone,
    Boolean active
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
