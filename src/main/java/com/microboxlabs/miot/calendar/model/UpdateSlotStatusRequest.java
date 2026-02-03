package com.microboxlabs.miot.calendar.model;

/**
 * Request to update slot status
 */
public record UpdateSlotStatusRequest(
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
