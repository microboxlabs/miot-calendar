package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to move an existing booking to a different slot, optionally refreshing the resource
 * payload at the same time. The booking's id and calendar are unchanged; the resource cannot be
 * swapped (the request's {@code resource.id} must match the booking's current resource id).
 */
@Schema(description = "Request payload to move an existing booking to a new slot")
public record MoveBookingRequest(
    @Schema(required = true, description = "Target slot date and time")
    SlotData slot,

    @Schema(description = "Optional resource payload to apply as part of the move. When omitted "
        + "the booking's existing resource fields are kept as-is. When provided the resource id "
        + "must match the booking's current resource id.")
    ResourceData resource
) {
    public void validate() {
        if (slot == null) {
            throw new IllegalArgumentException("Slot is required");
        }
        slot.validate();
        if (resource != null) {
            resource.validate();
        }
    }
}
