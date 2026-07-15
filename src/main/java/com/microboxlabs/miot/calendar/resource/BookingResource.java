package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.Booking;
import com.microboxlabs.miot.calendar.model.*;
import com.microboxlabs.miot.calendar.service.BookingService;
import com.microboxlabs.miot.calendar.validation.BookingValidationService.BookingValidationException;
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
 * REST resource for booking operations
 */
@Path("/api/v1/miot-calendar/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Booking creation, cancellation, and query operations")
public class BookingResource {

    private static final Logger LOG = Logger.getLogger(BookingResource.class);

    @Inject
    BookingService bookingService;

    @GET
    @Operation(operationId = "listBookings", summary = "List bookings", description = "Retrieve bookings filtered by calendar and date range. Defaults to the next 30 days if no dates are provided.")
    @APIResponse(responseCode = "200", description = "List of bookings",
        content = @Content(schema = @Schema(implementation = BookingListResponse.class)))
    public Response listBookings(
            @Parameter(description = "Filter bookings by calendar identifier", schema = @Schema(format = "uuid")) @QueryParam("calendarId") UUID calendarId,
            @Parameter(description = "Start date of the range (inclusive, defaults to today)", schema = @Schema(format = "date")) @QueryParam("startDate") LocalDate startDate,
            @Parameter(description = "End date of the range (inclusive, defaults to start + 30 days)", schema = @Schema(format = "date")) @QueryParam("endDate") LocalDate endDate,
            @Parameter(description = "Filter bookings by lifecycle status") @QueryParam("status") String status) {

        // Default to today if no dates provided
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(30);
        }

        BookingStatus statusFilter;
        try {
            statusFilter = BookingStatus.parse(status);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        }

        List<Booking> bookings = bookingService.getBookings(calendarId, startDate, endDate, statusFilter);
        return Response.ok(BookingListResponse.from(bookings)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(operationId = "getBooking", summary = "Get booking by ID", description = "Retrieve a specific booking by its unique identifier")
    @APIResponse(responseCode = "200", description = "Booking found",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "404", description = "Booking not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getBooking(
            @Parameter(description = "Unique identifier of the booking", required = true) @PathParam("id") UUID id) {
        return bookingService.getBookingById(id)
            .map(booking -> Response.ok(BookingResponse.from(booking)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Booking not found: " + id))
                .build());
    }

    @POST
    @Transactional
    @Operation(operationId = "createBooking", summary = "Create booking", description = "Create a new resource booking for a specific calendar and slot")
    @APIResponse(responseCode = "201", description = "Booking created",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Booking conflict (e.g., slot full or duplicate resource booking)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response createBooking(BookingRequest request,
            @Parameter(description = "Identifier of the user creating the booking") @HeaderParam("X-User-Id") String userId) {
        try {
            Booking booking = bookingService.createBooking(request, userId);
            return Response.status(Response.Status.CREATED)
                .entity(BookingResponse.from(booking))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid booking request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (BookingValidationException e) {
            LOG.warnf("Booking validation failed: %s (%s)", e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(operationId = "updateBooking", summary = "Update booking resource data and/or status", description = "Update an existing booking in place: its resource payload, its lifecycle status, or both. The slot is not changed; use POST /bookings/{id}/move to move a booking (or update its payload as part of a move). Status moves are forward-only; the only way back to PLANNED is a move to a different slot.")
    @APIResponse(responseCode = "200", description = "Booking updated",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (e.g. empty body, unknown status, or resource id mismatch)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Booking not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Status regression rejected",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response updateBooking(
            @Parameter(description = "Unique identifier of the booking to update", required = true) @PathParam("id") UUID id,
            BookingUpdateRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            request.validate();
            Booking booking = bookingService.updateBookingResource(id, request.resource(), request.status());
            return Response.ok(BookingResponse.from(booking)).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Booking not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            LOG.warnf("Invalid booking update request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (BookingValidationException e) {
            LOG.warnf("Booking status update rejected: %s (%s)", e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/{id}/move")
    @Transactional
    @Operation(operationId = "moveBooking", summary = "Move booking",
        description = "Move an existing booking to a different slot in the same calendar (and "
            + "optionally refresh its resource payload) as a single transactional operation. The "
            + "booking id is preserved across the move; no row is created or deleted. A same-slot "
            + "request collapses to a payload-only update (occupancy is not touched).")
    @APIResponse(responseCode = "200", description = "Booking moved",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (e.g. missing slot, resource id mismatch, slot not found in this calendar)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Booking not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Move violates validation (target full, blocked, resource already at target, etc.)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response moveBooking(
            @Parameter(description = "Unique identifier of the booking to move", required = true) @PathParam("id") UUID id,
            MoveBookingRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            Booking booking = bookingService.moveBooking(id, request);
            return Response.ok(BookingResponse.from(booking)).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Booking not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            LOG.warnf("Invalid booking move request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (BookingValidationException e) {
            LOG.warnf("Move validation failed: %s (%s)", e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(operationId = "cancelBooking", summary = "Cancel booking", description = "Cancel an existing booking and release the slot capacity")
    @APIResponse(responseCode = "204", description = "Booking cancelled")
    @APIResponse(responseCode = "404", description = "Booking not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response cancelBooking(
            @Parameter(description = "Unique identifier of the booking to cancel", required = true) @PathParam("id") UUID id) {
        try {
            bookingService.cancelBooking(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound(e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/resource/{resourceId}")
    @Operation(operationId = "listBookingsByResource", summary = "Get bookings by resource", description = "Retrieve all bookings associated with a specific resource identifier")
    @APIResponse(responseCode = "200", description = "List of bookings for the resource",
        content = @Content(schema = @Schema(implementation = BookingListResponse.class)))
    public Response getBookingsByResource(
            @Parameter(description = "Identifier of the resource to get bookings for", required = true) @PathParam("resourceId") String resourceId) {
        List<Booking> bookings = bookingService.getBookingsByResourceId(resourceId);
        return Response.ok(BookingListResponse.from(bookings)).build();
    }

    @PATCH
    @Path("/resource/{resourceId}")
    @Transactional
    @Operation(operationId = "patchBookingsByResource", summary = "Patch bookings by resource",
        description = "Patch every booking of a resource, addressed by the resource's external id "
            + "(optionally scoped to one calendar via calendarId). resourceData is shallow-merged "
            + "(top-level keys overwrite, absent keys are preserved); status follows the same "
            + "forward-only rules as PUT /bookings/{id}. Idempotent: re-sending the same patch "
            + "returns 200 with the same end state.")
    @APIResponse(responseCode = "200", description = "Patched bookings",
        content = @Content(schema = @Schema(implementation = BookingListResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (empty patch or unknown status)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "No booking matches the resource (and calendar scope)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Status regression rejected",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response patchBookingsByResource(
            @Parameter(description = "Identifier of the resource whose bookings to patch", required = true) @PathParam("resourceId") String resourceId,
            @Parameter(description = "Restrict the patch to bookings in this calendar", schema = @Schema(format = "uuid")) @QueryParam("calendarId") UUID calendarId,
            BookingResourcePatchRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            request.validate();
            List<Booking> bookings = bookingService.patchBookingsByResource(
                resourceId, calendarId, request.resourceData(), request.status());
            return Response.ok(BookingListResponse.from(bookings)).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Booking not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage()))
                    .build();
            }
            LOG.warnf("Invalid booking resource patch: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (BookingValidationException e) {
            LOG.warnf("Booking resource patch rejected: %s (%s)", e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }
}
