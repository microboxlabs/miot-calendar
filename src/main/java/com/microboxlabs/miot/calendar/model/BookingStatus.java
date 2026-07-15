package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Lifecycle status of a booking (CALSYNC).
 *
 * <p>The workflow coordinator (single writer) advances a booking forward
 * through {@code PLANNED → ASSIGNED → IN_TRANSIT → ARRIVED → FINISHED}.
 * {@code CANCELLED} is terminal and reachable from any other status (an
 * administrative annulment can even follow FINISHED). Regressions are
 * rejected, with one documented exception: a re-plan that moves the booking
 * to a different slot resets it to PLANNED (handled by the move operation,
 * not by a status update).
 */
@Schema(description = "Lifecycle status of a booking, advanced by the workflow coordinator")
public enum BookingStatus {
    PLANNED(0),
    ASSIGNED(1),
    IN_TRANSIT(2),
    ARRIVED(3),
    FINISHED(4),
    CANCELLED(5);

    private final int rank;

    BookingStatus(int rank) {
        this.rank = rank;
    }

    /**
     * Parse a raw status string. Returns null for null/blank (meaning "not
     * provided"); throws IllegalArgumentException with the allowed values for
     * anything that is not an exact status name.
     */
    public static BookingStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown booking status: " + raw + " (allowed: PLANNED, ASSIGNED, IN_TRANSIT, ARRIVED, FINISHED, CANCELLED)");
        }
    }

    /**
     * Whether a status update from this status to {@code to} is allowed.
     * Same-status is allowed (callers treat it as a no-op); CANCELLED is
     * reachable from anything but leads nowhere; otherwise only forward moves.
     */
    public boolean canTransitionTo(BookingStatus to) {
        if (to == this) {
            return true;
        }
        if (this == CANCELLED) {
            return false;
        }
        if (to == CANCELLED) {
            return true;
        }
        return to.rank > this.rank;
    }
}
