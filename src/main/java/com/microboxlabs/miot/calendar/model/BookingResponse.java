package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Booking;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a single booking
 */
@Schema(description = "Booking data returned by the API")
public record BookingResponse(
    @Schema(required = true, description = "Unique identifier of the booking", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Identifier of the calendar the booking belongs to", format = "uuid")
    UUID calendarId,

    @Schema(required = true, description = "Resource that was booked")
    ResourceData resource,

    @Schema(required = true, description = "Slot date and time of the booking")
    SlotData slot,

    @Schema(required = true, description = "Timestamp when the booking was created", format = "date-time")
    ZonedDateTime createdAt,

    @Schema(description = "Identifier of the user who created the booking")
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
