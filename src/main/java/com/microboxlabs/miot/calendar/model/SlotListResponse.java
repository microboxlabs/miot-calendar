package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Slot;

import java.util.List;

/**
 * Response for a list of slots
 */
public record SlotListResponse(
    List<SlotResponse> data,
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
