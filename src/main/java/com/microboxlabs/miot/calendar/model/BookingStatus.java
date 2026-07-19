package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Lifecycle status of a booking (CALSYNC).
 *
 * <p>The workflow coordinator (single writer) advances a booking forward
 * through {@code PLANNED → ASSIGNED → IN_TRANSIT → ARRIVED → FINISHED}.
 * {@code CANCELLED} is terminal and reachable from any other status (an
 * administrative annulment can even follow FINISHED).
 *
 * <p>Regressions are rejected, with two documented exceptions — both carried
 * by a dedicated <i>operation</i> rather than by a raw status update, so that
 * a stray late patch can never walk a booking backward:
 * <ul>
 *   <li>a re-plan that moves the booking to a different slot resets it to
 *       PLANNED (the move operation);</li>
 *   <li>an unassign that drops the carrier/driver/truck while keeping the
 *       slot resets it to PLANNED (the unassign operation, see
 *       {@link #canUnassign()}).</li>
 * </ul>
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

    /**
     * Whether an <b>unassign</b> may reset this booking to {@link #PLANNED} —
     * the second sanctioned regression, alongside the re-plan move.
     *
     * <p>The coordinator's workflow is not monotonic: a service can go back
     * from {@code presentDriver} to {@code assignDriver}, dropping its
     * carrier/driver/truck while keeping its slot. That is an unassign, and it
     * is the only reason a booking legitimately walks {@code ASSIGNED →
     * PLANNED}.
     *
     * <p>Deliberately narrower than "any regression": from {@code PLANNED} it
     * is an idempotent no-op, and from {@code ASSIGNED} it is the real reset.
     * From {@code IN_TRANSIT} onward the truck is already moving, so a
     * coordinator asking to unassign means the booking and the workflow have
     * genuinely diverged — that stays a rejected {@code STATUS_REGRESSION}
     * rather than being papered over.
     */
    public boolean canUnassign() {
        return this == PLANNED || this == ASSIGNED;
    }
}
