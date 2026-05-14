package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Booking;
import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.BookingRequest;
import com.microboxlabs.miot.calendar.model.ResourceData;
import com.microboxlabs.miot.calendar.validation.BookingValidationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for booking operations
 */
@ApplicationScoped
public class BookingService {

    private static final Logger LOG = Logger.getLogger(BookingService.class);

    @Inject
    BookingValidationService validationService;

    @Inject
    SlotService slotService;

    /**
     * Get bookings by calendar and date range
     */
    public List<Booking> getBookings(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        if (calendarId != null) {
            return Booking.findByCalendarAndDateRange(calendarId, startDate, endDate);
        }
        return Booking.findByDateRange(startDate, endDate);
    }

    /**
     * Get booking by ID
     */
    public Optional<Booking> getBookingById(UUID id) {
        return Optional.ofNullable(Booking.findById(id));
    }

    /**
     * Get bookings by resource ID
     */
    public List<Booking> getBookingsByResourceId(String resourceId) {
        return Booking.findByResourceId(resourceId);
    }

    /**
     * Create a new booking
     */
    @Transactional
    public Booking createBooking(BookingRequest request, String createdBy) {
        request.validate();

        // Get or find the slot
        Slot slot = slotService.getSlotByDateTime(
            request.calendarId(),
            request.slot().date(),
            request.slot().hour(),
            request.slot().minutes()
        ).orElseThrow(() -> new IllegalArgumentException(
            String.format("Slot not found for calendar %s at %s %02d:%02d",
                request.calendarId(),
                request.slot().date(),
                request.slot().hour(),
                request.slot().minutes()
            )
        ));

        // Validate the booking. When the request is part of a reassignment, the client passes
        // the soon-to-be-cancelled old booking's id so the window-capacity check doesn't double-
        // count it (the cancel runs after this create succeeds).
        validationService.validateBooking(slot, request.resource().id(), request.excludeBookingId());

        // Get calendar
        Calendar calendar = Calendar.findById(request.calendarId());
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + request.calendarId());
        }

        // Create the booking
        Booking booking = new Booking();
        booking.slot = slot;
        booking.calendar = calendar;
        booking.slotDate = slot.slotDate;
        booking.slotHour = slot.slotHour;
        booking.slotMinutes = slot.slotMinutes;
        booking.resourceId = request.resource().id();
        booking.resourceType = request.resource().type();
        booking.resourceLabel = request.resource().label();
        booking.resourceData = request.resource().data();
        booking.createdBy = createdBy;

        booking.persist();

        // Increment slot occupancy
        slotService.incrementOccupancy(slot);

        LOG.infof("Created booking %s for resource %s in slot %s",
            booking.id, booking.resourceId, slot.id);

        return booking;
    }

    /**
     * Update an existing booking's resource payload in place.
     *
     * <p>Only {@code resourceType}, {@code resourceLabel} and {@code resourceData}
     * change — the slot stays the same. The request's resource id must match the
     * booking's current resource id; a booking cannot be repointed to a different
     * resource (use cancel + create for that).
     */
    @Transactional
    public Booking updateBookingResource(UUID bookingId, ResourceData resource) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }

        resource.validate();
        if (!resource.id().equals(booking.resourceId)) {
            throw new IllegalArgumentException(String.format(
                "Resource id mismatch: booking %s is for resource %s, cannot update to %s",
                bookingId, booking.resourceId, resource.id()));
        }

        booking.resourceType = resource.type();
        booking.resourceLabel = resource.label();
        booking.resourceData = resource.data();
        booking.persist();

        LOG.infof("Updated booking %s resource data for resource %s", booking.id, booking.resourceId);

        return booking;
    }

    /**
     * Cancel a booking
     */
    @Transactional
    public void cancelBooking(UUID bookingId) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }

        Slot slot = booking.slot;
        
        // Delete the booking
        booking.delete();

        // Decrement slot occupancy
        slotService.decrementOccupancy(slot);

        LOG.infof("Cancelled booking %s for resource %s", bookingId, booking.resourceId);
    }
}
