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
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for planned services - backward compatibility with original API spec
 * This is an alias for BookingResource with service-specific response format
 */
@Path("/api/planned-services")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Planned Services", description = "Planned service endpoints (alias for bookings)")
public class PlannedServiceResource {

    private static final Logger LOG = Logger.getLogger(PlannedServiceResource.class);

    @Inject
    BookingService bookingService;

    /**
     * Response format matching the original API spec
     */
    public record PlannedServiceItem(
        String id,
        Map<String, Object> service,
        SlotData slot
    ) {
        public static PlannedServiceItem from(Booking booking) {
            // Build service object from resource data
            Map<String, Object> service = booking.resourceData != null 
                ? booking.resourceData 
                : Map.of();
            
            // Ensure id is in the service object
            if (!service.containsKey("id")) {
                service = new java.util.HashMap<>(service);
                service.put("id", booking.resourceId);
            }

            return new PlannedServiceItem(
                booking.id.toString(),
                service,
                new SlotData(booking.slotDate, booking.slotHour, booking.slotMinutes)
            );
        }
    }

    public record PlannedServiceListResponse(
        List<PlannedServiceItem> data,
        Long total
    ) {}

    @GET
    @Operation(summary = "List planned services", description = "Get planned services filtered by date range")
    @APIResponse(responseCode = "200", description = "List of planned services",
        content = @Content(schema = @Schema(implementation = PlannedServiceListResponse.class)))
    public Response listPlannedServices(
            @Parameter(description = "Start date (ISO format)") @QueryParam("startDate") LocalDate startDate,
            @Parameter(description = "End date (ISO format)") @QueryParam("endDate") LocalDate endDate) {
        
        // Default to today if no dates provided
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (endDate == null) {
            endDate = startDate.plusDays(30);
        }

        List<Booking> bookings = bookingService.getBookings(null, startDate, endDate);
        List<PlannedServiceItem> items = bookings.stream()
            .map(PlannedServiceItem::from)
            .toList();
        
        return Response.ok(new PlannedServiceListResponse(items, (long) items.size())).build();
    }

    /**
     * Request format matching the original API spec
     */
    public record PlannedServiceRequest(
        Map<String, Object> service,
        SlotData slot
    ) {
        public BookingRequest toBookingRequest(UUID calendarId) {
            String serviceId = service.get("id") != null ? service.get("id").toString() : null;
            String cliente = service.get("cliente") != null ? service.get("cliente").toString() : null;
            String origen = service.get("origen") != null ? service.get("origen").toString() : null;
            String destino = service.get("destino") != null ? service.get("destino").toString() : null;
            
            String label = cliente != null 
                ? String.format("%s - %s to %s", cliente, origen, destino)
                : serviceId;

            ResourceData resource = new ResourceData(
                serviceId,
                "SERVICE",
                label,
                service
            );
            
            return new BookingRequest(calendarId, resource, slot);
        }
    }

    @POST
    @Transactional
    @Operation(summary = "Create planned service", description = "Create a new planned service booking")
    @APIResponse(responseCode = "201", description = "Planned service created",
        content = @Content(schema = @Schema(implementation = PlannedServiceItem.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "409", description = "Booking conflict")
    public Response createPlannedService(
            PlannedServiceRequest request,
            @Parameter(description = "Calendar ID (defaults to first active calendar if not provided)") 
            @QueryParam("calendarId") UUID calendarId,
            @Parameter(description = "User creating the booking") 
            @HeaderParam("X-User-Id") String userId) {
        try {
            // If no calendar specified, we need to determine which one to use
            // For now, require calendarId or get the first active one
            if (calendarId == null) {
                throw new IllegalArgumentException("calendarId query parameter is required");
            }

            // Validate request
            if (request.service() == null) {
                throw new IllegalArgumentException("Service is required");
            }
            if (request.slot() == null) {
                throw new IllegalArgumentException("Slot is required");
            }
            if (request.service().get("id") == null) {
                throw new IllegalArgumentException("Service ID is required");
            }
            request.slot().validate();

            BookingRequest bookingRequest = request.toBookingRequest(calendarId);
            Booking booking = bookingService.createBooking(bookingRequest, userId);
            
            return Response.status(Response.Status.CREATED)
                .entity(PlannedServiceItem.from(booking))
                .build();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid planned service request: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.badRequest(e.getMessage()))
                .build();
        } catch (BookingValidationException e) {
            LOG.warnf("Planned service validation failed: %s (%s)", e.getMessage(), e.getErrorCode());
            return Response.status(Response.Status.CONFLICT)
                .entity(ErrorResponse.conflict(e.getMessage()))
                .build();
        }
    }
}
