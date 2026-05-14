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

    @Schema(description = "Identifier of an existing booking to exclude from window-capacity "
        + "counting. Used during reassignment: the client creates the new booking before "
        + "cancelling the old one, so the validator must not double-count the old booking "
        + "against the window's day-cap.", format = "uuid")
    UUID excludeBookingId
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
