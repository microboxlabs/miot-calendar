package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.CalendarGroup;
import com.microboxlabs.miot.calendar.model.CalendarGroupRequest;
import com.microboxlabs.miot.calendar.model.CalendarGroupResponse;
import com.microboxlabs.miot.calendar.model.ErrorResponse;
import com.microboxlabs.miot.calendar.service.CalendarGroupService;
import jakarta.inject.Inject;
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
 * REST resource for calendar group management
 */
@Path("/api/v1/miot-calendar/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Calendar Groups", description = "Calendar group management operations")
public class CalendarGroupResource {

    private static final Logger LOG = Logger.getLogger(CalendarGroupResource.class);

    @Inject
    CalendarGroupService calendarGroupService;

    @GET
    @Operation(operationId = "listGroups", summary = "List calendar groups", description = "Retrieve all calendar groups, optionally filtered by active status")
    @APIResponse(responseCode = "200", description = "List of calendar groups",
        content = @Content(schema = @Schema(implementation = CalendarGroupResponse[].class)))
    public Response listGroups(
            @Parameter(description = "When true, return only active groups") @QueryParam("active") Boolean active) {
        List<CalendarGroup> groups = active != null && active
            ? calendarGroupService.getActiveGroups()
            : calendarGroupService.getAllGroups();

        List<CalendarGroupResponse> response = groups.stream()
            .map(CalendarGroupResponse::from)
            .toList();

        return Response.ok(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getGroup", summary = "Get group by ID", description = "Retrieve a specific calendar group by its unique identifier")
    @APIResponse(responseCode = "200", description = "Group found",
        content = @Content(schema = @Schema(implementation = CalendarGroupResponse.class)))
    @APIResponse(responseCode = "404", description = "Group not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getGroup(
            @Parameter(description = "Unique identifier of the group", required = true) @PathParam("id") UUID id) {
        return calendarGroupService.getGroupById(id)
            .map(group -> Response.ok(CalendarGroupResponse.from(group)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Group not found: " + id))
                .build());
    }

    @POST
    @Operation(operationId = "createGroup", summary = "Create calendar group", description = "Create a new calendar group")
    @APIResponse(responseCode = "201", description = "Group created",
        content = @Content(schema = @Schema(implementation = CalendarGroupResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request or duplicate code",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createGroup(CalendarGroupRequest request) {
        try {
            CalendarGroup group = calendarGroupService.createGroup(request);
            return Response.status(Response.Status.CREATED)
                .entity(CalendarGroupResponse.from(group))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid group request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(operationId = "updateGroup", summary = "Update calendar group", description = "Update an existing calendar group")
    @APIResponse(responseCode = "200", description = "Group updated",
        content = @Content(schema = @Schema(implementation = CalendarGroupResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Group not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateGroup(
            @Parameter(description = "Unique identifier of the group to update", required = true) @PathParam("id") UUID id,
            CalendarGroupRequest request) {
        try {
            CalendarGroup group = calendarGroupService.updateGroup(id, request);
            return Response.ok(CalendarGroupResponse.from(group)).build();
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
    @Operation(operationId = "deactivateGroup", summary = "Deactivate calendar group", description = "Soft-delete a calendar group by setting it as inactive")
    @APIResponse(responseCode = "204", description = "Group deactivated")
    @APIResponse(responseCode = "404", description = "Group not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response deactivateGroup(
            @Parameter(description = "Unique identifier of the group to deactivate", required = true) @PathParam("id") UUID id) {
        try {
            calendarGroupService.deactivateGroup(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }
}
