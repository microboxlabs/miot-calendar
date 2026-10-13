package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * How much of a time window's daily capacity is taken on one date
 */
@Schema(description = "Booked and remaining capacity of a time window on one date")
public record TimeWindowUsageResponse(
    @Schema(required = true, description = "Identifier of the time window", format = "uuid")
    UUID timeWindowId,

    @Schema(required = true, description = "Date the usage applies to", format = "date")
    LocalDate date,

    @Schema(required = true, description = "Booking capacity of the window for the date", minimum = "0")
    Integer capacity,

    @Schema(required = true, description = "Bookings counted against the capacity "
        + "(overbooked bookings are not counted)", minimum = "0")
    Integer booked,

    @Schema(required = true, description = "Remaining capacity; never negative", minimum = "0")
    Integer available
) {
    public static TimeWindowUsageResponse of(UUID timeWindowId, LocalDate date, int capacity, int booked) {
        return new TimeWindowUsageResponse(timeWindowId, date, capacity, booked, Math.max(0, capacity - booked));
    }
}
