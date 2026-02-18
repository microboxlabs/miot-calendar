package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Slot;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a slot
 */
@Schema(description = "Booking slot data returned by the API")
public record SlotResponse(
    @Schema(required = true, description = "Unique identifier of the slot", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Identifier of the calendar this slot belongs to", format = "uuid")
    UUID calendarId,

    @Schema(description = "Identifier of the time window that generated this slot", format = "uuid")
    UUID timeWindowId,

    @Schema(required = true, description = "Date of the slot", format = "date")
    LocalDate slotDate,

    @Schema(required = true, description = "Hour of the slot (0-23)", minimum = "0", maximum = "23")
    Integer slotHour,

    @Schema(required = true, description = "Minutes of the slot", minimum = "0", maximum = "59")
    Integer slotMinutes,

    @Schema(required = true, description = "Maximum booking capacity for this slot", minimum = "1")
    Integer capacity,

    @Schema(required = true, description = "Number of current bookings in this slot", minimum = "0")
    Integer currentOccupancy,

    @Schema(required = true, description = "Remaining available capacity", minimum = "0")
    Integer availableCapacity,

    @Schema(required = true, description = "Current status of the slot")
    SlotStatus status,

    @Schema(required = true, description = "Timestamp when the slot was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, description = "Timestamp when the slot was last updated", format = "date-time")
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
