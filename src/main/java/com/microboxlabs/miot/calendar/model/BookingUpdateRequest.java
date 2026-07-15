package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to update an existing booking in place: its resource payload,
 * its lifecycle status, or both. The slot is not changed by this operation;
 * moving a booking to another slot is done via {@code POST /bookings/{id}/move}.
 */
@Schema(description = "Request payload to update a booking's resource data and/or lifecycle status in place")
public record BookingUpdateRequest(
    @Schema(description = "Resource payload to store on the booking (omit to leave unchanged)")
    ResourceData resource,

    @Schema(description = "Target lifecycle status (omit to leave unchanged; regressions are rejected)", examples = {"IN_TRANSIT"})
    String status
) {
    /** Back-compat canonical shape without a status. */
    public BookingUpdateRequest(ResourceData resource) {
        this(resource, null);
    }

    /**
     * Validate the update request
     */
    public void validate() {
        if (resource == null && (status == null || status.isBlank())) {
            throw new IllegalArgumentException("At least one of resource or status is required");
        }
        if (resource != null) {
            resource.validate();
        }
        // Throws with the allowed values when the status is unknown
        BookingStatus.parse(status);
    }
}
