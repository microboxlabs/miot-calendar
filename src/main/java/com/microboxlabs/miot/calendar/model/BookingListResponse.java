package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.Booking;

import java.util.List;

/**
 * Response for a list of bookings
 */
public record BookingListResponse(
    List<BookingResponse> data,
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
