package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Request to create or update a time window
 */
@Schema(description = "Request payload to create or update a time window within a calendar")
public record TimeWindowRequest(
    @Schema(required = true, description = "Name of the time window", examples = {"Morning Loading Window"}, maxLength = 255)
    String name,

    @Schema(required = true, description = "Start hour of the time window (inclusive)", minimum = "0", maximum = "23", examples = {"8"})
    Integer startHour,

    @Schema(required = true, description = "End hour of the time window (exclusive)", minimum = "0", maximum = "23", examples = {"12"})
    Integer endHour,

    @Schema(description = "Total number of services this window can handle across all slots", minimum = "1", examples = {"20"}, defaultValue = "1")
    Integer capacity,

    @Schema(description = "Comma-separated days of the week (1=Monday to 7=Sunday)", examples = {"1,2,3,4,5"})
    String daysOfWeek,

    @Schema(required = true, description = "Date from which this time window is valid", format = "date", examples = {"2025-01-01"})
    LocalDate validFrom,

    @Schema(description = "Date until which this time window is valid (null means no end date)", format = "date", examples = {"2025-12-31"})
    LocalDate validTo,

    @Schema(description = "Whether the time window is active", defaultValue = "true")
    Boolean active,

    @Schema(description = "UI color token for displaying this time window (e.g., \"emerald\", \"amber\")", examples = {"emerald"}, maxLength = 32)
    String color,

    @Schema(description = "Discriminator: WINDOW (bookable, default) or BLOCK (non-bookable)", defaultValue = "WINDOW")
    TimeWindowKind kind
) {
    /**
     * Validate the time window request.
     *
     * BLOCK windows accept null/0 capacity since they generate non-bookable
     * slots; WINDOW windows still require capacity >= 1.
     */
    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Time window name is required");
        }
        if (startHour == null || startHour < 0 || startHour > 23) {
            throw new IllegalArgumentException("Start hour must be between 0 and 23");
        }
        if (endHour == null || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("End hour must be between 0 and 23");
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("Valid from date is required");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("Valid to date must be after valid from date");
        }
        validateCapacity();
    }

    private void validateCapacity() {
        if (capacity == null) {
            return;
        }
        int min = kind == TimeWindowKind.BLOCK ? 0 : 1;
        if (capacity < min) {
            throw new IllegalArgumentException("Capacity must be at least " + min);
        }
    }
}
