package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Acknowledgement state of the booking's <b>current</b> data by an external
 * downstream system that mirrors this calendar. Orthogonal to the monotonic
 * lifecycle {@link BookingStatus}: a booking can be ASSIGNED yet unconfirmed
 * downstream, and a data change legitimately drops back to PENDING, which the
 * forward-only status ladder cannot express. There are deliberately no
 * transition rules here — the integration layer that projects bookings
 * outward is the single writer and owns the sequencing.
 *
 * <p>Null (column absent) = untracked: calendars with no external mirror, or
 * bookings created before the feature.
 */
@Schema(description = "External-system acknowledgement of the booking's current data")
public enum BookingSyncStatus {
    /** A synchronization is queued or in flight; not acknowledged yet. */
    PENDING,
    /** The external system acknowledged the booking's current data. */
    CONFIRMED,
    /** The external system refused it; see {@code syncDetail} for the reason. */
    REJECTED;

    /**
     * Parse a raw value. Returns null for null/blank (meaning "not provided");
     * throws IllegalArgumentException with the allowed values otherwise.
     */
    public static BookingSyncStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BookingSyncStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown booking sync status: " + raw + " (allowed: PENDING, CONFIRMED, REJECTED)");
        }
    }
}
