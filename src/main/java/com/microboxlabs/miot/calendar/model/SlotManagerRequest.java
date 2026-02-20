package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to create or update a slot manager
 */
@Schema(description = "Request payload to create or update a slot manager")
public record SlotManagerRequest(
    @Schema(required = true, description = "Calendar ID to manage slot generation for", format = "uuid")
    UUID calendarId,

    @Schema(description = "Enable or disable automatic slot generation", defaultValue = "true")
    Boolean active,

    @Schema(description = "How many days ahead to keep slots generated", defaultValue = "30", minimum = "1")
    Integer daysInAdvance,

    @Schema(description = "Maximum days to generate per scheduler run batch", defaultValue = "7", minimum = "1")
    Integer batchDays,

    @Schema(description = "Force slot regeneration from this date (one-shot, cleared after successful run)", format = "date")
    LocalDate reprocessFrom,

    @Schema(description = "Force slot regeneration through this date (required when reprocessFrom is set)", format = "date")
    LocalDate reprocessTo
) {
    /** Full validation for create */
    public void validate() {
        if (calendarId == null) {
            throw new IllegalArgumentException("calendarId is required");
        }
        if (daysInAdvance != null && daysInAdvance < 1) {
            throw new IllegalArgumentException("daysInAdvance must be at least 1");
        }
        if (batchDays != null && batchDays < 1) {
            throw new IllegalArgumentException("batchDays must be at least 1");
        }
        validateReprocess();
    }

    /** Partial validation for update */
    public void validateUpdate() {
        if (daysInAdvance != null && daysInAdvance < 1) {
            throw new IllegalArgumentException("daysInAdvance must be at least 1");
        }
        if (batchDays != null && batchDays < 1) {
            throw new IllegalArgumentException("batchDays must be at least 1");
        }
        validateReprocess();
    }

    private void validateReprocess() {
        if (reprocessFrom != null && reprocessTo == null) {
            throw new IllegalArgumentException("reprocessTo is required when reprocessFrom is set");
        }
        if (reprocessTo != null && reprocessFrom == null) {
            throw new IllegalArgumentException("reprocessFrom is required when reprocessTo is set");
        }
        if (reprocessFrom != null && reprocessFrom.isAfter(reprocessTo)) {
            throw new IllegalArgumentException("reprocessFrom must not be after reprocessTo");
        }
    }
}
