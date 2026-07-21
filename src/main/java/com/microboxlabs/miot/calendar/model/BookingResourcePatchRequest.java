package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

/**
 * Request to patch every booking of a resource by the resource's external id
 * (CALSYNC C2). This is the coordinator-facing mutation: the workflow knows
 * the service code, not the booking UUID, so it addresses bookings by
 * {@code resourceId} (optionally scoped to one calendar).
 *
 * <p>{@code resourceData} is shallow-merged: top-level keys overwrite the
 * stored payload, absent keys are preserved. {@code status} follows the same
 * forward-only rules as the UUID-keyed update. {@code syncStatus} is the
 * orthogonal external-sync dimension: no transition rules (the integration
 * layer that mirrors bookings outward is the single writer and owns the
 * sequencing), and setting it stamps {@code syncAt}.
 */
@Schema(description = "Patch applied to all bookings of a resource: shallow-merged resource data, a lifecycle status and/or an external-sync status")
public record BookingResourcePatchRequest(
    @Schema(description = "Top-level keys to merge into resource.data (omit to leave the payload unchanged)")
    Map<String, Object> resourceData,

    @Schema(description = "Target lifecycle status (omit to leave unchanged; regressions are rejected)", examples = {"FINISHED"})
    String status,

    @Schema(description = "External-system acknowledgement of the booking's current data (omit to leave unchanged)", examples = {"CONFIRMED"})
    String syncStatus,

    @Schema(description = "External-system acknowledgement/rejection detail, stored alongside syncStatus")
    String syncDetail
) {
    /**
     * Validate the patch request
     */
    public void validate() {
        boolean hasData = resourceData != null && !resourceData.isEmpty();
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSyncStatus = syncStatus != null && !syncStatus.isBlank();
        if (!hasData && !hasStatus && !hasSyncStatus) {
            throw new IllegalArgumentException(
                "At least one of resourceData, status or syncStatus is required");
        }
        // Throw with the allowed values when either status is unknown
        BookingStatus.parse(status);
        BookingSyncStatus.parse(syncStatus);
    }
}
