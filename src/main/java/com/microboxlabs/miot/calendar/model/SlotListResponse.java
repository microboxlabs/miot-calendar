package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Slot;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response for a list of slots
 */
@Schema(description = "Paginated list of slots")
public record SlotListResponse(
    @Schema(required = true, description = "List of slot records")
    List<SlotResponse> data,

    @Schema(required = true, description = "Total number of slots in the result", minimum = "0")
    Long total
) {
    /**
     * Create from list of entities
     */
    public static SlotListResponse from(List<Slot> slots) {
        List<SlotResponse> data = slots.stream()
            .map(SlotResponse::from)
            .toList();
        return new SlotListResponse(data, (long) data.size());
    }
}
