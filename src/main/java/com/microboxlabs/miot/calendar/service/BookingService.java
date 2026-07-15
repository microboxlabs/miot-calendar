package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Booking;
import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.BookingRequest;
import com.microboxlabs.miot.calendar.model.BookingStatus;
import com.microboxlabs.miot.calendar.model.MoveBookingRequest;
import com.microboxlabs.miot.calendar.model.ResourceData;
import com.microboxlabs.miot.calendar.validation.BookingValidationService;
import com.microboxlabs.miot.calendar.validation.BookingValidationService.BookingValidationException;
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

        // Validate the booking
        validationService.validateBooking(slot, request.resource().id());

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
        BookingStatus initialStatus = BookingStatus.parse(request.status());
        booking.status = initialStatus != null ? initialStatus : BookingStatus.PLANNED;
        booking.createdBy = createdBy;

        booking.persist();

        // Increment slot occupancy
        slotService.incrementOccupancy(slot);

        LOG.infof("Created booking %s for resource %s in slot %s",
            booking.id, booking.resourceId, slot.id);

        return booking;
    }

    /**
     * Move an existing booking to a different slot in the same calendar, optionally refreshing the
     * resource payload at the same time.
     *
     * <p>The booking id is preserved across the move — a {@code POST /bookings/{id}/move} call
     * never creates or deletes a row. This is what makes reassignment atomic from the client's
     * perspective: the planner no longer has to issue a create + cancel pair where a failed
     * cancel would leave a ghost booking.
     *
     * <p>When the target slot equals the current slot the call collapses to a resource-only
     * update (no occupancy changes, no move-validation). A {@code null} {@code request.resource()}
     * leaves the existing payload untouched; when provided the resource id must match the
     * booking's current resource id (a booking cannot be repointed to a different resource).
     */
    @Transactional
    public Booking moveBooking(UUID bookingId, MoveBookingRequest request) {
        request.validate();

        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }

        ResourceData newResource = request.resource();
        if (newResource != null && !newResource.id().equals(booking.resourceId)) {
            throw new IllegalArgumentException(String.format(
                "Resource id mismatch: booking %s is for resource %s, cannot move with resource %s",
                bookingId, booking.resourceId, newResource.id()));
        }

        Slot oldSlot = booking.slot;
        Slot newSlot = slotService.getSlotByDateTime(
            booking.calendar.id,
            request.slot().date(),
            request.slot().hour(),
            request.slot().minutes()
        ).orElseThrow(() -> new IllegalArgumentException(
            String.format("Slot not found for calendar %s at %s %02d:%02d",
                booking.calendar.id,
                request.slot().date(),
                request.slot().hour(),
                request.slot().minutes()
            )
        ));

        boolean slotChanged = !oldSlot.id.equals(newSlot.id);

        if (slotChanged) {
            validationService.validateMove(oldSlot, newSlot, booking.resourceId);
        }

        if (slotChanged) {
            booking.slot = newSlot;
            booking.slotDate = newSlot.slotDate;
            booking.slotHour = newSlot.slotHour;
            booking.slotMinutes = newSlot.slotMinutes;
            // The documented status-regression exception: a re-plan to a new
            // slot restarts the lifecycle. Same-slot payload refreshes keep
            // the current status.
            booking.status = BookingStatus.PLANNED;
        }
        if (newResource != null) {
            booking.resourceType = newResource.type();
            booking.resourceLabel = newResource.label();
            booking.resourceData = newResource.data();
        }
        booking.persist();

        if (slotChanged) {
            slotService.decrementOccupancy(oldSlot);
            slotService.incrementOccupancy(newSlot);
            LOG.infof("Moved booking %s for resource %s from slot %s to slot %s",
                booking.id, booking.resourceId, oldSlot.id, newSlot.id);
        } else {
            LOG.infof("Updated booking %s payload in place (slot unchanged)", booking.id);
        }

        return booking;
    }

    /**
     * Update an existing booking in place: its resource payload, its
     * lifecycle status, or both.
     *
     * <p>The slot stays the same. When a resource is given its id must match
     * the booking's current resource id; a booking cannot be repointed to a
     * different resource (use cancel + create for that). When a status is
     * given it must be a forward transition (same-status is a no-op);
     * regressions throw a {@link BookingValidationException} with code
     * {@code STATUS_REGRESSION} — the only sanctioned way back to PLANNED is
     * a move to a different slot (see {@link #moveBooking}).
     */
    @Transactional
    public Booking updateBookingResource(UUID bookingId, ResourceData resource, String rawStatus) {
        Booking booking = Booking.findById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found: " + bookingId);
        }

        if (resource != null) {
            resource.validate();
            if (!resource.id().equals(booking.resourceId)) {
                throw new IllegalArgumentException(String.format(
                    "Resource id mismatch: booking %s is for resource %s, cannot update to %s",
                    bookingId, booking.resourceId, resource.id()));
            }
        }

        BookingStatus targetStatus = BookingStatus.parse(rawStatus);
        if (targetStatus != null && targetStatus != booking.status) {
            if (!booking.status.canTransitionTo(targetStatus)) {
                throw new BookingValidationException(String.format(
                    "Status regression: booking %s is %s, cannot go back to %s",
                    bookingId, booking.status, targetStatus), "STATUS_REGRESSION");
            }
            booking.status = targetStatus;
        }

        if (resource != null) {
            booking.resourceType = resource.type();
            booking.resourceLabel = resource.label();
            booking.resourceData = resource.data();
        }
        booking.persist();

        LOG.infof("Updated booking %s (resource %s, status %s)",
            booking.id, booking.resourceId, booking.status);

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
