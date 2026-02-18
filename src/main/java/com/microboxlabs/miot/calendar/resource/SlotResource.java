package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.*;
import com.microboxlabs.miot.calendar.service.SlotGeneratorService;
import com.microboxlabs.miot.calendar.service.SlotService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for slot management
 */
@Path("/api/v1/miot-calendar/slots")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Slots", description = "Slot querying, generation, and status management operations")
public class SlotResource {

    private static final Logger LOG = Logger.getLogger(SlotResource.class);

    @Inject
    SlotService slotService;

    @Inject
    SlotGeneratorService slotGeneratorService;

    @GET
    @Operation(operationId = "listSlots", summary = "List slots", description = "Retrieve slots for a calendar, filtered by date range and availability. Defaults to the next 7 days if no dates are provided.")
    @APIResponse(responseCode = "200", description = "List of slots",
        content = @Content(schema = @Schema(implementation = SlotListResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (e.g., missing calendarId)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response listSlots(
            @Parameter(description = "Identifier of the calendar to list slots for", required = true, schema = @Schema(format = "uuid")) @QueryParam("calendarId") UUID calendarId,
            @Parameter(description = "Start date of the range (inclusive, defaults to today)", schema = @Schema(format = "date")) @QueryParam("startDate") LocalDate startDate,
            @Parameter(description = "End date of the range (inclusive, defaults to start + 7 days)", schema = @Schema(format = "date")) @QueryParam("endDate") LocalDate endDate,
            @Parameter(description = "When true, return only slots with available capacity") @QueryParam("available") Boolean availableOnly) {

        if (calendarId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest("calendarId is required"))
                .build();
        }

        // Default to today if no dates provided
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(7);
        }

        List<Slot> slots = availableOnly != null && availableOnly
            ? slotService.getAvailableSlots(calendarId, startDate, endDate)
            : slotService.getSlots(calendarId, startDate, endDate);

        return Response.ok(SlotListResponse.from(slots)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getSlot", summary = "Get slot by ID", description = "Retrieve a specific slot by its unique identifier")
    @APIResponse(responseCode = "200", description = "Slot found",
        content = @Content(schema = @Schema(implementation = SlotResponse.class)))
    @APIResponse(responseCode = "404", description = "Slot not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getSlot(
            @Parameter(description = "Unique identifier of the slot", required = true) @PathParam("id") UUID id) {
        return slotService.getSlotById(id)
            .map(slot -> Response.ok(SlotResponse.from(slot)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Slot not found: " + id))
                .build());
    }

    @POST
    @Path("/generate")
    @Transactional
    @Operation(operationId = "generateSlots", summary = "Generate slots", description = "Generate booking slots for a calendar within a date range based on its time window configurations. Existing slots are skipped.")
    @APIResponse(responseCode = "200", description = "Slots generated successfully",
        content = @Content(schema = @Schema(implementation = GenerateSlotsResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response generateSlots(GenerateSlotsRequest request) {
        try {
            request.validate();
            GenerateSlotsResponse response = slotGeneratorService.generateSlots(
                request.calendarId(),
                request.startDate(),
                request.endDate()
            );
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid generate slots request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    @Operation(operationId = "updateSlotStatus", summary = "Update slot status", description = "Manually close or reopen a slot. Status can only be set to OPEN or CLOSED; FULL is managed automatically.")
    @APIResponse(responseCode = "200", description = "Slot status updated",
        content = @Content(schema = @Schema(implementation = SlotResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid status value",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Slot not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateSlotStatus(
            @Parameter(description = "Unique identifier of the slot to update", required = true) @PathParam("id") UUID id,
            UpdateSlotStatusRequest request) {
        try {
            request.validate();
            Slot slot = slotService.updateSlotStatus(id, request.status());
            return Response.ok(SlotResponse.from(slot)).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }
}
