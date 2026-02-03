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
@Path("/api/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Bookings", description = "Booking management endpoints")
public class BookingResource {

    private static final Logger LOG = Logger.getLogger(BookingResource.class);

    @Inject
    BookingService bookingService;

    @GET
    @Operation(summary = "List bookings", description = "Get bookings filtered by calendar and date range")
    @APIResponse(responseCode = "200", description = "List of bookings",
        content = @Content(schema = @Schema(implementation = BookingListResponse.class)))
    public Response listBookings(
            @Parameter(description = "Calendar ID") @QueryParam("calendarId") UUID calendarId,
            @Parameter(description = "Start date (ISO format)") @QueryParam("startDate") LocalDate startDate,
            @Parameter(description = "End date (ISO format)") @QueryParam("endDate") LocalDate endDate) {
        
        // Default to today if no dates provided
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(30);
        }

        List<Booking> bookings = bookingService.getBookings(calendarId, startDate, endDate);
        return Response.ok(BookingListResponse.from(bookings)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get booking by ID", description = "Retrieve a specific booking")
    @APIResponse(responseCode = "200", description = "Booking found",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "404", description = "Booking not found")
    public Response getBooking(@PathParam("id") UUID id) {
        return bookingService.getBookingById(id)
            .map(booking -> Response.ok(BookingResponse.from(booking)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.notFound("Booking not found: " + id))
                .build());
    }

    @POST
    @Transactional
    @Operation(summary = "Create booking", description = "Create a new resource booking")
    @APIResponse(responseCode = "201", description = "Booking created",
        content = @Content(schema = @Schema(implementation = BookingResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "409", description = "Booking conflict")
    public Response createBooking(BookingRequest request,
            @Parameter(description = "User creating the booking") @HeaderParam("X-User-Id") String userId) {
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

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Cancel booking", description = "Cancel an existing booking")
    @APIResponse(responseCode = "204", description = "Booking cancelled")
    @APIResponse(responseCode = "404", description = "Booking not found")
    public Response cancelBooking(@PathParam("id") UUID id) {
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
    @Operation(summary = "Get bookings by resource", description = "Get all bookings for a specific resource")
    @APIResponse(responseCode = "200", description = "List of bookings")
    public Response getBookingsByResource(@PathParam("resourceId") String resourceId) {
        List<Booking> bookings = bookingService.getBookingsByResourceId(resourceId);
        return Response.ok(BookingListResponse.from(bookings)).build();
    }
}
