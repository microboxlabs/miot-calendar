package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TMS (Alerce) confirmation state of a booking's <b>current</b> assignment
 * tuple — orthogonal to the monotonic lifecycle {@link BookingStatus}: a
 * booking can be ASSIGNED yet unconfirmed, and a re-assignment legitimately
 * drops back to PENDING, which the forward-only status ladder cannot express.
 * There are deliberately no transition rules here — the coordinator's job
 * chain is the single writer and its ordering (chain gate on the async-job
 * ledger) is the sequencing authority.
 *
 * <p>Null (column absent) = untracked: calendars without an integrated
 * origin, or bookings created before the feature.
 */
@Schema(description = "TMS confirmation of the booking's current assignment tuple")
public enum BookingSyncStatus {
    /** A push is queued or in flight; the TMS has not confirmed yet. */
    PENDING,
    /** The TMS accepted the tuple (push acknowledged with code=OK). */
    CONFIRMED,
    /** The TMS refused the tuple; see {@code syncDetail} for the reason. */
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
