package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

/**
 * Generic resource data for bookings
 */
@Schema(description = "Resource being booked (e.g., a truck, shipment, or cargo container)")
public record ResourceData(
    @Schema(required = true, description = "Unique identifier of the resource", examples = {"truck-4521"})
    String id,

    @Schema(description = "Type of the resource", examples = {"truck"})
    String type,

    @Schema(description = "Human-readable label for the resource", examples = {"Truck AB-1234"})
    String label,

    @Schema(description = "Additional resource metadata as key-value pairs")
    Map<String, Object> data
) {
    /**
     * Validate resource data
     */
    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resource ID is required");
        }
    }
}
