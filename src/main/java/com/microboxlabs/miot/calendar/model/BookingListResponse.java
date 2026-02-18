package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Booking;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response for a list of bookings
 */
@Schema(description = "Paginated list of bookings")
public record BookingListResponse(
    @Schema(required = true, description = "List of booking records")
    List<BookingResponse> data,

    @Schema(required = true, description = "Total number of bookings in the result", minimum = "0")
    Long total
) {
    /**
     * Create from list of entities
     */
    public static BookingListResponse from(List<Booking> bookings) {
        List<BookingResponse> data = bookings.stream()
            .map(BookingResponse::from)
            .toList();
        return new BookingListResponse(data, (long) data.size());
    }
}
