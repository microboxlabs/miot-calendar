package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response for slot generation
 */
@Schema(description = "Result of a slot generation operation")
public record GenerateSlotsResponse(
    @Schema(required = true, description = "Number of new slots created", minimum = "0", examples = {"120"})
    Integer slotsCreated,

    @Schema(required = true, description = "Number of slots skipped because they already existed", minimum = "0", examples = {"10"})
    Integer slotsSkipped,

    @Schema(required = true, description = "Human-readable summary of the generation result", examples = {"Generated 120 new slots, 10 already existed"})
    String message
) {
    public static GenerateSlotsResponse of(int created, int skipped) {
        String message = String.format("Generated %d new slots, %d already existed", created, skipped);
        return new GenerateSlotsResponse(created, skipped, message);
    }
}
