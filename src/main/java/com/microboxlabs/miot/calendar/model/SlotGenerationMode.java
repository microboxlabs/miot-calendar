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
     * filled with {@code floor(windowMinutes / slotDurationMinutes)} slots; only the first
     * {@code ceil(capacity / parallelism)} are bookable ({@code OPEN}) — the remainder are generated
     * as {@link SlotStatus#OVERFLOW} so the planning grid can render them, but bookings against
     * those slots are rejected.
     */
    MANUAL
}
