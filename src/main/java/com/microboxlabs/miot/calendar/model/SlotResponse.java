package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Slot;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a slot
 */
public record SlotResponse(
    UUID id,
    UUID calendarId,
    UUID timeWindowId,
    LocalDate slotDate,
    Integer slotHour,
    Integer slotMinutes,
    Integer capacity,
    Integer currentOccupancy,
    Integer availableCapacity,
    SlotStatus status,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    /**
     * Create from entity
     */
    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
            slot.id,
            slot.calendar.id,
            slot.timeWindow != null ? slot.timeWindow.id : null,
            slot.slotDate,
            slot.slotHour,
            slot.slotMinutes,
            slot.capacity,
            slot.currentOccupancy,
            slot.capacity - slot.currentOccupancy,
            slot.status,
            slot.createdAt,
            slot.updatedAt
        );
    }
}
