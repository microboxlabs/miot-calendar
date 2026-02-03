package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.*;
import com.microboxlabs.miot.calendar.service.CalendarService;
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

import java.util.List;
import java.util.UUID;

/**
 * REST resource for calendar management
 */
@Path("/api/calendars")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Calendars", description = "Calendar management endpoints")
public class CalendarResource {

    private static final Logger LOG = Logger.getLogger(CalendarResource.class);

    @Inject
    CalendarService calendarService;

    @GET
    @Operation(summary = "List calendars", description = "Get all calendars or active calendars only")
    @APIResponse(responseCode = "200", description = "List of calendars")
    public Response listCalendars(
            @Parameter(description = "Filter active calendars only") @QueryParam("active") Boolean active) {
        List<Calendar> calendars = active != null && active 
            ? calendarService.getActiveCalendars()
            : calendarService.getAllCalendars();
        
        List<CalendarResponse> response = calendars.stream()
            .map(CalendarResponse::from)
            .toList();
        
        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get calendar by ID", description = "Retrieve a specific calendar")
    @APIResponse(responseCode = "200", description = "Calendar found",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "404", description = "Calendar not found")
    public Response getCalendar(@PathParam("id") UUID id) {
        return calendarService.getCalendarById(id)
            .map(calendar -> Response.ok(CalendarResponse.from(calendar)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Calendar not found: " + id))
                .build());
    }

    @POST
    @Transactional
    @Operation(summary = "Create calendar", description = "Create a new calendar")
    @APIResponse(responseCode = "201", description = "Calendar created",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    public Response createCalendar(CalendarRequest request) {
        try {
            Calendar calendar = calendarService.createCalendar(request);
            return Response.status(Response.Status.CREATED)
                .entity(CalendarResponse.from(calendar))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid calendar request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Update calendar", description = "Update an existing calendar")
    @APIResponse(responseCode = "200", description = "Calendar updated",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "404", description = "Calendar not found")
    public Response updateCalendar(@PathParam("id") UUID id, CalendarRequest request) {
        try {
            Calendar calendar = calendarService.updateCalendar(id, request);
            return Response.ok(CalendarResponse.from(calendar)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Deactivate calendar", description = "Deactivate a calendar (soft delete)")
    @APIResponse(responseCode = "204", description = "Calendar deactivated")
    @APIResponse(responseCode = "404", description = "Calendar not found")
    public Response deactivateCalendar(@PathParam("id") UUID id) {
        try {
            calendarService.deactivateCalendar(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    // Time Window endpoints

    @GET
    @Path("/{calendarId}/time-windows")
    @Operation(summary = "List time windows", description = "Get time windows for a calendar")
    @APIResponse(responseCode = "200", description = "List of time windows")
    public Response listTimeWindows(@PathParam("calendarId") UUID calendarId) {
        List<TimeWindow> timeWindows = calendarService.getTimeWindows(calendarId);
        List<TimeWindowResponse> response = timeWindows.stream()
            .map(TimeWindowResponse::from)
            .toList();
        return Response.ok(response).build();
    }

    @POST
    @Path("/{calendarId}/time-windows")
    @Transactional
    @Operation(summary = "Create time window", description = "Create a new time window for a calendar")
    @APIResponse(responseCode = "201", description = "Time window created",
        content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "404", description = "Calendar not found")
    public Response createTimeWindow(@PathParam("calendarId") UUID calendarId, TimeWindowRequest request) {
        try {
            TimeWindow timeWindow = calendarService.createTimeWindow(calendarId, request);
            return Response.status(Response.Status.CREATED)
                .entity(TimeWindowResponse.from(timeWindow))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid time window request: %s", e.getMessage());
            if (e.getMessage().contains("Calendar not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{calendarId}/time-windows/{timeWindowId}")
    @Transactional
    @Operation(summary = "Update time window", description = "Update an existing time window")
    @APIResponse(responseCode = "200", description = "Time window updated",
        content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @APIResponse(responseCode = "404", description = "Time window not found")
    public Response updateTimeWindow(
            @PathParam("calendarId") UUID calendarId,
            @PathParam("timeWindowId") UUID timeWindowId,
            TimeWindowRequest request) {
        try {
            TimeWindow timeWindow = calendarService.updateTimeWindow(timeWindowId, request);
            return Response.ok(TimeWindowResponse.from(timeWindow)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }
}
