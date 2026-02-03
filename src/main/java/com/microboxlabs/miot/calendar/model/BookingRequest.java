package com.microboxlabs.miot.calendar.model;

import java.util.UUID;

/**
 * Request to create a booking
 */
public record BookingRequest(
    UUID calendarId,
    ResourceData resource,
    SlotData slot
) {
    /**
     * Validate the booking request
     */
    public void validate() {
        if (calendarId == null) {
            throw new IllegalArgumentException("Calendar ID is required");
        }
        if (resource == null) {
            throw new IllegalArgumentException("Resource is required");
        }
        if (slot == null) {
            throw new IllegalArgumentException("Slot is required");
        }
        resource.validate();
        slot.validate();
    }
}
