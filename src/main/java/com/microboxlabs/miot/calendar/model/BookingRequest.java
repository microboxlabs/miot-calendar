package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Request to create a booking
 */
@Schema(description = "Request payload to create a new booking")
public record BookingRequest(
    @Schema(required = true, description = "Identifier of the calendar to book in", format = "uuid")
    UUID calendarId,

    @Schema(required = true, description = "Resource being booked")
    ResourceData resource,

    @Schema(required = true, description = "Slot date and time to book")
    SlotData slot,

    @Schema(description = "Initial lifecycle status (defaults to PLANNED)", examples = {"PLANNED"})
    String status
) {
    /** Back-compat canonical shape without a status (defaults to PLANNED). */
    public BookingRequest(UUID calendarId, ResourceData resource, SlotData slot) {
        this(calendarId, resource, slot, null);
    }

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
        // Throws with the allowed values when the status is unknown
        BookingStatus.parse(status);
    }
}
