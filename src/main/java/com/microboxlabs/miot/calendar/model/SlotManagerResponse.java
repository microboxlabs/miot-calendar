package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.SlotManager;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a slot manager
 */
@Schema(description = "Slot manager configuration and last run state")
public record SlotManagerResponse(
    @Schema(required = true, description = "Unique identifier of the slot manager", format = "uuid")
    UUID id,

    @Schema(required = true, description = "Calendar ID being managed", format = "uuid")
    UUID calendarId,

    @Schema(required = true, description = "Calendar code")
    String calendarCode,

    @Schema(required = true, description = "Calendar name")
    String calendarName,

    @Schema(required = true, description = "Whether automatic generation is enabled")
    Boolean active,

    @Schema(required = true, description = "Days ahead to keep slots generated")
    Integer daysInAdvance,

    @Schema(required = true, description = "Max days generated per batch")
    Integer batchDays,

    @Schema(description = "One-shot reprocess start date (cleared after successful run)", format = "date")
    LocalDate reprocessFrom,

    @Schema(description = "One-shot reprocess end date (cleared after successful run)", format = "date")
    LocalDate reprocessTo,

    @Schema(description = "Timestamp of the last run attempt", format = "date-time")
    ZonedDateTime lastRunAt,

    @Schema(description = "Status of the last run: IDLE, RUNNING, SUCCESS, FAILED, SKIPPED")
    String lastRunStatus,

    @Schema(description = "Error message from the last failed run")
    String lastRunError,

    @Schema(description = "Latest date through which slots have been generated", format = "date")
    LocalDate generatedThrough,

    @Schema(required = true, format = "date-time")
    ZonedDateTime createdAt,

    @Schema(required = true, format = "date-time")
    ZonedDateTime updatedAt
) {
    public static SlotManagerResponse from(SlotManager m) {
        return new SlotManagerResponse(
            m.id,
            m.calendar.id,
            m.calendar.code,
            m.calendar.name,
            m.active,
            m.daysInAdvance,
            m.batchDays,
            m.reprocessFrom,
            m.reprocessTo,
            m.lastRunAt,
            m.lastRunStatus != null ? m.lastRunStatus.name() : null,
            m.lastRunError,
            m.generatedThrough,
            m.createdAt,
            m.updatedAt
        );
    }
}
