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
     * Legacy status: previously assigned to MANUAL-mode slots generated beyond the window's
     * bookable quota. No longer produced — MANUAL windows now emit every slot as {@code OPEN} and
     * enforce the window's total capacity at booking time. Kept so existing rows (and the booking
     * validator's defensive guard) still resolve; new code should not depend on it.
     */
    OVERFLOW
}
