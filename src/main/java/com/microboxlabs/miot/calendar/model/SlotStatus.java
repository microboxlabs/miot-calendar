package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Slot status enum
 */
@Schema(description = "Status of a booking slot", enumeration = {"OPEN", "FULL", "CLOSED"})
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
    CLOSED
}
