package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to update slot status
 */
@Schema(description = "Request payload to update the status of a slot")
public record UpdateSlotStatusRequest(
    @Schema(required = true, description = "New status for the slot (only OPEN or CLOSED allowed)")
    SlotStatus status
) {
    public void validate() {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
        // Only allow manual status changes to OPEN or CLOSED
        if (status == SlotStatus.FULL) {
            throw new IllegalArgumentException("Cannot manually set status to FULL");
        }
    }
}
