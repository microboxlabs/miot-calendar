package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.SlotManager;
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
import java.util.Set;
import java.util.UUID;

/**
 * REST resource for calendar management
 */
@Path("/api/v1/miot-calendar/calendars")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Calendars", description = "Calendar and time window management operations")
public class CalendarResource {

    private static final Logger LOG = Logger.getLogger(CalendarResource.class);

    @Inject
    CalendarService calendarService;

    @GET
    @Transactional
    @Operation(operationId = "listCalendars", summary = "List calendars", description = "Retrieve all calendars, optionally filtered by active status or group code")
    @APIResponse(responseCode = "200", description = "List of calendars",
        content = @Content(schema = @Schema(implementation = CalendarResponse[].class)))
    public Response listCalendars(
            @Parameter(description = "When true, return only active calendars") @QueryParam("active") Boolean active,
            @Parameter(description = "Filter calendars belonging to this group code") @QueryParam("groupCode") String groupCode) {
        List<Calendar> calendars;
        if (groupCode != null && !groupCode.isBlank()) {
            calendars = calendarService.getCalendarsByGroupCode(groupCode);
        } else {
            calendars = active != null && active
                ? calendarService.getActiveCalendars()
                : calendarService.getAllCalendars();
        }

        Set<UUID> idsWithManager = SlotManager.findCalendarIdsWithManager(
            calendars.stream().map(c -> c.id).toList());

        List<CalendarResponse> response = calendars.stream()
            .map(c -> CalendarResponse.from(c, idsWithManager.contains(c.id)))
            .toList();

        return Response.ok(response).build();
    }

    @GET
    @Path("/default")
    @Transactional
    @Operation(operationId = "getDefaultCalendar", summary = "Get the default calendar for an origin",
        description = "Resolve the calendar an integrating system should book into when it was given none. "
            + "Matches the active default whose filter names this origin, falling back to the default with no "
            + "origin filter. Answers 204 when there is none: nothing is configured to receive those bookings, "
            + "so none should be created. Deliberately NOT a 404 — a client must be able to tell that answer "
            + "apart from talking to a server that has no such endpoint, which is a failure to fall back from "
            + "rather than a decision to act on.")
    @APIResponse(responseCode = "200", description = "Default calendar for the origin",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "204", description = "No default calendar for this origin")
    public Response getDefaultCalendar(
            @Parameter(description = "Origin the booking belongs to, matched against the calendar filter's origin")
            @QueryParam("origin") String origin) {
        Calendar calendar = calendarService.getDefaultCalendarForOrigin(origin);
        if (calendar == null) {
            LOG.debugf("No default calendar configured for origin: %s", origin);
            return Response.noContent().build();
        }
        return Response.ok(CalendarResponse.from(calendar, hasSlotManager(calendar.id))).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    @Operation(operationId = "getCalendar", summary = "Get calendar by ID", description = "Retrieve a specific calendar by its unique identifier")
    @APIResponse(responseCode = "200", description = "Calendar found",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "404", description = "Calendar not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getCalendar(
            @Parameter(description = "Unique identifier of the calendar", required = true) @PathParam("id") UUID id) {
        return calendarService.getCalendarById(id)
            .map(calendar -> Response.ok(CalendarResponse.from(calendar, hasSlotManager(calendar.id))).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Calendar not found: " + id))
                .build());
    }

    @POST
    @Transactional
    @Operation(operationId = "createCalendar", summary = "Create calendar", description = "Create a new calendar with the provided configuration")
    @APIResponse(responseCode = "201", description = "Calendar created",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createCalendar(CalendarRequest request) {
        try {
            Calendar calendar = calendarService.createCalendar(request);
            return Response.status(Response.Status.CREATED)
                .entity(CalendarResponse.from(calendar, hasSlotManager(calendar.id)))
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
    @Operation(operationId = "updateCalendar", summary = "Update calendar", description = "Update an existing calendar's configuration")
    @APIResponse(responseCode = "200", description = "Calendar updated",
        content = @Content(schema = @Schema(implementation = CalendarResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Calendar not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateCalendar(
            @Parameter(description = "Unique identifier of the calendar to update", required = true) @PathParam("id") UUID id,
            CalendarRequest request) {
        try {
            Calendar calendar = calendarService.updateCalendar(id, request);
            return Response.ok(CalendarResponse.from(calendar, hasSlotManager(calendar.id))).build();
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

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(operationId = "deactivateCalendar", summary = "Deactivate calendar", description = "Soft-delete a calendar by setting it as inactive")
    @APIResponse(responseCode = "204", description = "Calendar deactivated")
    @APIResponse(responseCode = "404", description = "Calendar not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response deactivateCalendar(
            @Parameter(description = "Unique identifier of the calendar to deactivate", required = true) @PathParam("id") UUID id) {
        try {
            calendarService.deactivateCalendar(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("/{id}/purge")
    @Operation(
        operationId = "hardDeleteCalendar",
        summary = "Permanently delete calendar",
        description = "Irreversibly deletes the calendar and all its associated data (slots, bookings, time windows, slot manager)."
    )
    @APIResponse(responseCode = "204", description = "Calendar permanently deleted")
    @APIResponse(responseCode = "404", description = "Calendar not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response hardDeleteCalendar(
            @Parameter(description = "Unique identifier of the calendar to permanently delete", required = true)
            @PathParam("id") UUID id) {
        try {
            calendarService.hardDeleteCalendar(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    private boolean hasSlotManager(UUID calendarId) {
        return SlotManager.findByCalendarId(calendarId) != null;
    }

    // Time Window endpoints

    @GET
    @Path("/{calendarId}/time-windows")
    @Operation(operationId = "listTimeWindows", summary = "List time windows", description = "Retrieve all time windows configured for a specific calendar")
    @APIResponse(responseCode = "200", description = "List of time windows",
        content = @Content(schema = @Schema(implementation = TimeWindowResponse[].class)))
    public Response listTimeWindows(
            @Parameter(description = "Unique identifier of the calendar", required = true) @PathParam("calendarId") UUID calendarId) {
        List<TimeWindow> timeWindows = calendarService.getTimeWindows(calendarId);
        List<TimeWindowResponse> response = timeWindows.stream()
            .map(TimeWindowResponse::from)
            .toList();
        return Response.ok(response).build();
    }

    @POST
    @Path("/{calendarId}/time-windows")
    @Transactional
    @Operation(operationId = "createTimeWindow", summary = "Create time window", description = "Create a new time window within a calendar to define when slots can be generated")
    @APIResponse(responseCode = "201", description = "Time window created",
        content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Calendar not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createTimeWindow(
            @Parameter(description = "Unique identifier of the calendar", required = true) @PathParam("calendarId") UUID calendarId,
            TimeWindowRequest request) {
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
    @Operation(operationId = "updateTimeWindow", summary = "Update time window", description = "Update an existing time window's configuration")
    @APIResponse(responseCode = "200", description = "Time window updated",
        content = @Content(schema = @Schema(implementation = TimeWindowResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Time window not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateTimeWindow(
            @Parameter(description = "Unique identifier of the calendar", required = true) @PathParam("calendarId") UUID calendarId,
            @Parameter(description = "Unique identifier of the time window to update", required = true) @PathParam("timeWindowId") UUID timeWindowId,
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
