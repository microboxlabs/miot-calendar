package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.SlotManager;
import com.microboxlabs.miot.calendar.entity.SlotManagerRun;
import com.microboxlabs.miot.calendar.model.ErrorResponse;
import com.microboxlabs.miot.calendar.model.SlotManagerRequest;
import com.microboxlabs.miot.calendar.model.SlotManagerResponse;
import com.microboxlabs.miot.calendar.model.SlotManagerRunResponse;
import com.microboxlabs.miot.calendar.service.SlotManagerExecutor;
import com.microboxlabs.miot.calendar.service.SlotManagerService;
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
 * REST resource for slot manager configuration and manual triggers.
 */
@Path("/api/v1/miot-calendar/slot-managers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Slot Managers", description = "Automatic slot generation manager configuration and triggers")
public class SlotManagerResource {

    private static final Logger LOG = Logger.getLogger(SlotManagerResource.class);

    @Inject
    SlotManagerService slotManagerService;

    @Inject
    SlotManagerExecutor slotManagerExecutor;

    // ── Manager CRUD ───────────────────────────────────────────────────────

    @GET
    @Operation(operationId = "listSlotManagers", summary = "List slot managers",
        description = "Retrieve all slot managers, optionally filtered by active status")
    @APIResponse(responseCode = "200", description = "List of slot managers",
        content = @Content(schema = @Schema(implementation = SlotManagerResponse[].class)))
    public Response listSlotManagers(
            @Parameter(description = "When true, return only active managers")
            @QueryParam("active") Boolean active) {

        List<SlotManager> managers = active != null && active
            ? slotManagerService.getActiveManagers()
            : slotManagerService.getAllManagers();

        return Response.ok(managers.stream().map(SlotManagerResponse::from).toList()).build();
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getSlotManager", summary = "Get slot manager by ID")
    @APIResponse(responseCode = "200", description = "Slot manager found",
        content = @Content(schema = @Schema(implementation = SlotManagerResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getSlotManager(
            @Parameter(description = "Slot manager ID", required = true) @PathParam("id") UUID id) {
        return slotManagerService.getManagerById(id)
            .map(m -> Response.ok(SlotManagerResponse.from(m)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Slot manager not found: " + id))
                .build());
    }

    @POST
    @Operation(operationId = "createSlotManager", summary = "Create slot manager",
        description = "Create an automatic slot generation manager for a calendar")
    @APIResponse(responseCode = "201", description = "Created",
        content = @Content(schema = @Schema(implementation = SlotManagerResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request or duplicate",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createSlotManager(SlotManagerRequest request) {
        try {
            SlotManager manager = slotManagerService.createManager(request);
            return Response.status(Response.Status.CREATED)
                .entity(SlotManagerResponse.from(manager))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid slot manager request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(operationId = "updateSlotManager", summary = "Update slot manager",
        description = "Update configuration. Set reprocessFrom + reprocessTo to schedule one-shot slot regeneration.")
    @APIResponse(responseCode = "200", description = "Updated",
        content = @Content(schema = @Schema(implementation = SlotManagerResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateSlotManager(
            @Parameter(description = "Slot manager ID", required = true) @PathParam("id") UUID id,
            SlotManagerRequest request) {
        try {
            SlotManager manager = slotManagerService.updateManager(id, request);
            return Response.ok(SlotManagerResponse.from(manager)).build();
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
    @Operation(operationId = "deactivateSlotManager", summary = "Deactivate slot manager",
        description = "Soft-delete: disables automatic generation without removing history")
    @APIResponse(responseCode = "204", description = "Deactivated")
    @APIResponse(responseCode = "404", description = "Not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response deactivateSlotManager(
            @Parameter(description = "Slot manager ID", required = true) @PathParam("id") UUID id) {
        try {
            slotManagerService.deactivateManager(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    // ── Manual triggers ────────────────────────────────────────────────────

    @POST
    @Path("/run")
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "runAllSlotManagers", summary = "Trigger all active managers",
        description = "Synchronously runs all active slot managers. "
            + "Suitable for K8s CronJob usage — returns when all managers have completed.")
    @APIResponse(responseCode = "200", description = "Run results for each manager",
        content = @Content(schema = @Schema(implementation = SlotManagerRunResponse[].class)))
    public Response runAll() {
        List<SlotManagerRunResponse> results = slotManagerExecutor.runAll("API");
        return Response.ok(results).build();
    }

    @POST
    @Path("/{id}/run")
    @Consumes(MediaType.WILDCARD)
    @Operation(operationId = "runSlotManager", summary = "Trigger one manager",
        description = "Synchronously runs the specified slot manager and returns its run record.")
    @APIResponse(responseCode = "200", description = "Run record",
        content = @Content(schema = @Schema(implementation = SlotManagerRunResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response runOne(
            @Parameter(description = "Slot manager ID", required = true) @PathParam("id") UUID id) {
        try {
            SlotManagerRunResponse run = slotManagerExecutor.runManager(id, "API");
            return Response.ok(run).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    // ── Run history ────────────────────────────────────────────────────────

    @GET
    @Path("/runs")
    @Operation(operationId = "listAllRuns", summary = "List recent runs across all managers")
    @APIResponse(responseCode = "200", description = "Run records",
        content = @Content(schema = @Schema(implementation = SlotManagerRunResponse[].class)))
    public Response listAllRuns(
            @Parameter(description = "Maximum number of records to return")
            @QueryParam("limit") @DefaultValue("50") int limit) {

        List<SlotManagerRun> runs = slotManagerService.getRecentRuns(Math.min(limit, 200));
        return Response.ok(runs.stream().map(SlotManagerRunResponse::from).toList()).build();
    }

    @GET
    @Path("/{id}/runs")
    @Operation(operationId = "listRunsByManager", summary = "List runs for a specific manager")
    @APIResponse(responseCode = "200", description = "Run records",
        content = @Content(schema = @Schema(implementation = SlotManagerRunResponse[].class)))
    @APIResponse(responseCode = "404", description = "Manager not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response listRunsByManager(
            @Parameter(description = "Slot manager ID", required = true) @PathParam("id") UUID id,
            @Parameter(description = "Maximum number of records to return")
            @QueryParam("limit") @DefaultValue("20") int limit) {

        return slotManagerService.getManagerById(id)
            .map(m -> {
                List<SlotManagerRun> runs = slotManagerService.getRunsByManagerId(id, Math.min(limit, 200));
                return Response.ok(runs.stream().map(SlotManagerRunResponse::from).toList()).build();
            })
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Slot manager not found: " + id))
                .build());
    }
}
