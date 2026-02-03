package com.microboxlabs.miot.calendar.model;

import java.util.Map;

/**
 * Generic resource data for bookings
 */
public record ResourceData(
    String id,
    String type,
    String label,
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
