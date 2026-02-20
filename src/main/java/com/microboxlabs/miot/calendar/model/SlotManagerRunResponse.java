package com.microboxlabs.miot.calendar.model;

import com.microboxlabs.miot.calendar.entity.SlotManagerRun;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response for a single slot manager run record
 */
@Schema(description = "Record of a single slot manager execution")
public record SlotManagerRunResponse(
    @Schema(required = true, format = "uuid")
    UUID id,

    @Schema(required = true, format = "uuid")
    UUID managerId,

    @Schema(required = true, description = "What triggered this run: SCHEDULER, API, CLI")
    String triggeredBy,

    @Schema(required = true, format = "date-time")
    ZonedDateTime startedAt,

    @Schema(format = "date-time")
    ZonedDateTime finishedAt,

    @Schema(required = true, description = "RUNNING, SUCCESS, FAILED, SKIPPED")
    String status,

    @Schema(required = true, minimum = "0")
    Integer slotsCreated,

    @Schema(required = true, minimum = "0")
    Integer slotsSkipped,

    @Schema(format = "date")
    LocalDate generatedFrom,

    @Schema(format = "date")
    LocalDate generatedThrough,

    String errorMessage
) {
    public static SlotManagerRunResponse from(SlotManagerRun r) {
        return new SlotManagerRunResponse(
            r.id,
            r.manager.id,
            r.triggeredBy,
            r.startedAt,
            r.finishedAt,
            r.status != null ? r.status.name() : null,
            r.slotsCreated,
            r.slotsSkipped,
            r.generatedFrom,
            r.generatedThrough,
            r.errorMessage
        );
    }
}
