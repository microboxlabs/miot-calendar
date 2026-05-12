package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to update an existing booking's resource payload in place.
 * The slot is not changed by this operation; moving a booking to another
 * slot is done via cancel + create.
 */
@Schema(description = "Request payload to update a booking's resource data in place")
public record BookingUpdateRequest(
    @Schema(required = true, description = "Resource payload to store on the booking")
    ResourceData resource
) {
    /**
     * Validate the update request
     */
    public void validate() {
        if (resource == null) {
            throw new IllegalArgumentException("Resource is required");
        }
        resource.validate();
    }
}
