package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Slot status enum
 */
@Schema(description = "Status of a booking slot", enumeration = {"OPEN", "FULL", "CLOSED", "OVERFLOW"})
public enum SlotStatus {
    /**
     * Slot is open for bookings
     */
    OPEN,

    /**
     * Slot is at full capacity
     */
    FULL,

    /**
     * Slot is manually closed (not accepting bookings)
     */
    CLOSED,

    /**
     * Slot was generated beyond the time window's bookable quota (MANUAL slot generation):
     * it exists so the planning grid can render it, but it carries zero capacity and bookings
     * against it are rejected.
     */
    OVERFLOW
}
