package com.microboxlabs.miot.calendar.model;

/**
 * Slot status enum
 */
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
