package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Kind of time window", enumeration = {"WINDOW", "BLOCK"})
public enum TimeWindowKind {
    /**
     * Bookable window with capacity. Slots are generated as OPEN.
     */
    WINDOW,

    /**
     * Non-bookable period (holiday, maintenance, manual closure). Slot
     * generation produces CLOSED slots so the planning UI can render blocked
     * cells, and bookings against those slots are rejected.
     */
    BLOCK
}
