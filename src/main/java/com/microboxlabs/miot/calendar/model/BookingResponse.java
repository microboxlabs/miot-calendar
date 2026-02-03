package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Booking;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response for a single booking
 */
public record BookingResponse(
    UUID id,
    UUID calendarId,
    ResourceData resource,
    SlotData slot,
    ZonedDateTime createdAt,
    String createdBy
) {
    /**
     * Create from entity
     */
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
            booking.id,
            booking.calendar.id,
            new ResourceData(
                booking.resourceId,
                booking.resourceType,
                booking.resourceLabel,
                booking.resourceData
            ),
            new SlotData(
                booking.slotDate,
                booking.slotHour,
                booking.slotMinutes
            ),
            booking.createdAt,
            booking.createdBy
        );
    }
}
