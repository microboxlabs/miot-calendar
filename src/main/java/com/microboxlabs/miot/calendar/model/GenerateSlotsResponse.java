package com.microboxlabs.miot.calendar.model;

/**
 * Response for slot generation
 */
public record GenerateSlotsResponse(
    Integer slotsCreated,
    Integer slotsSkipped,
    String message
) {
    public static GenerateSlotsResponse of(int created, int skipped) {
        String message = String.format("Generated %d new slots, %d already existed", created, skipped);
        return new GenerateSlotsResponse(created, skipped, message);
    }
}
