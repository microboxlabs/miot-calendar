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
@Tag(name = "Slots", description = "Slot management endpoints")
public class SlotResource {

    private static final Logger LOG = Logger.getLogger(SlotResource.class);

    @Inject
    SlotService slotService;

    @Inject
    SlotGeneratorService slotGeneratorService;

    @GET
    @Operation(summary = "List slots", description = "Get slots filtered by calendar and date range")
    @APIResponse(responseCode = "200", description = "List of slots",
        content = @Content(schema = @Schema(implementation = SlotListResponse.class)))
    public Response listSlots(
            @Parameter(description = "Calendar ID", required = true) @QueryParam("calendarId") UUID calendarId,
            @Parameter(description = "Start date (ISO format)") @QueryParam("startDate") LocalDate startDate,
            @Parameter(description = "End date (ISO format)") @QueryParam("endDate") LocalDate endDate,
            @Parameter(description = "Show only available slots") @QueryParam("available") Boolean availableOnly) {
        
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
    @Operation(summary = "Get slot by ID", description = "Retrieve a specific slot")
    @APIResponse(responseCode = "200", description = "Slot found",
        content = @Content(schema = @Schema(implementation = SlotResponse.class)))
    @APIResponse(responseCode = "404", description = "Slot not found")
    public Response getSlot(@PathParam("id") UUID id) {
        return slotService.getSlotById(id)
            .map(slot -> Response.ok(SlotResponse.from(slot)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Slot not found: " + id))
                .build());
    }

    @POST
    @Path("/generate")
    @Transactional
    @Operation(summary = "Generate slots", description = "Generate slots for a calendar within a date range based on time window configuration")
    @APIResponse(responseCode = "200", description = "Slots generated",
        content = @Content(schema = @Schema(implementation = GenerateSlotsResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
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
    @Operation(summary = "Update slot status", description = "Close or reopen a slot")
    @APIResponse(responseCode = "200", description = "Slot status updated",
        content = @Content(schema = @Schema(implementation = SlotResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid status")
    @APIResponse(responseCode = "404", description = "Slot not found")
    public Response updateSlotStatus(@PathParam("id") UUID id, UpdateSlotStatusRequest request) {
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
