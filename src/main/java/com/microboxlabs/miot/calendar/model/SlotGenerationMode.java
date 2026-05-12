package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * How a time window's slot duration is determined.
 */
@Schema(description = "Slot generation mode for a time window", enumeration = {"AUTO", "MANUAL"})
public enum SlotGenerationMode {
    /**
     * Slot duration is derived from the window length and the capacity model:
     * {@code numberOfSlots = ceil(capacity / parallelism)},
     * {@code slotDurationMinutes = windowMinutes / numberOfSlots}. Every generated slot is bookable.
     */
    AUTO,

    /**
     * Slot duration is set explicitly by the admin ({@code slotDurationMinutes}). The window is
     * filled with {@code floor(windowMinutes / slotDurationMinutes)} slots, all bookable
     * ({@code OPEN}), each holding up to {@code parallelism} bookings. The window's {@code capacity}
     * is a cap on the <em>total</em> bookings across all of its slots for the date: once that count
     * is reached, no slot in the window accepts another booking, regardless of order. The slot grid
     * may therefore be larger than {@code capacity} — the surplus simply stays empty.
     */
    MANUAL
}
