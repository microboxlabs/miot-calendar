package com.microboxlabs.miot.calendar.validation;

import com.microboxlabs.miot.calendar.entity.Booking;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.SlotGenerationMode;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Service for validating bookings
 */
@ApplicationScoped
public class BookingValidationService {

    private static final Logger LOG = Logger.getLogger(BookingValidationService.class);

    /**
     * Validate a booking create request.
     *
     * @param slot The slot to book
     * @param resourceId The resource to book
     * @throws BookingValidationException if validation fails
     */
    public void validateBooking(Slot slot, String resourceId) {
        validateBooking(slot, resourceId, false);
    }

    /**
     * Validate a booking create request, optionally bypassing capacity limits.
     * Structural booking guards (closed/generated-overflow slots and duplicate
     * resources) always apply.
     */
    public void validateBooking(Slot slot, String resourceId, boolean allowOverbooking) {
        // 1. Check slot status
        validateSlotStatus(slot, allowOverbooking);

        if (!allowOverbooking) {
            // 2. Check the parent window's total-capacity cap (MANUAL windows)
            validateWindowCapacity(slot);

            // 3. Check slot capacity
            validateSlotCapacity(slot);
        }

        // 4. Check resource not already in slot
        validateResourceNotInSlot(slot, resourceId);

        // 5. Optional: Check resource not already booked on same day
        // This is commented out as it may not always be required
        // validateResourceNotBookedOnDate(slot.slotDate, resourceId);

        LOG.debugf("Booking validation passed for slot %s, resource %s", slot.id, resourceId);
    }

    /**
     * Validate moving an existing booking to {@code newSlot}.
     *
     * <p>A move is one booking row whose slot reference changes — the source has its occupancy
     * decremented, the target has its occupancy incremented, no row is created or deleted. So the
     * checks differ from create-path validation:
     *
     * <ul>
     *   <li><b>Slot status</b> on the target — same as create.
     *   <li><b>Window capacity</b> — skipped when the move stays inside the same window+date (the
     *       day-cap count is unchanged). For cross-window or cross-date moves the standard cap
     *       check applies to the target window only; the source can never overflow from a move.
     *   <li><b>Slot capacity</b> on the target — same as create.
     *   <li><b>Resource-not-in-target</b> — a row at the target with this resource is a duplicate
     *       only if it is not the booking being moved itself. Callers must only invoke this when
     *       the source and target slots differ, so the booking under move can never be at the
     *       target.
     * </ul>
     *
     * @param oldSlot The booking's current slot
     * @param newSlot The slot to move into; must differ from {@code oldSlot}
     * @param resourceId The booking's resource id (carried over from the existing booking)
     * @throws BookingValidationException if validation fails
     */
    public void validateMove(Slot oldSlot, Slot newSlot, String resourceId) {
        validateSlotStatus(newSlot);

        // A same-window+same-date move leaves the day-cap count unchanged (one booking out, one
        // booking in) so there is nothing to check. Cross-window or cross-date adds 1 to the
        // target window's count for the date; apply the standard cap check there.
        boolean sameWindowAndDate =
            oldSlot.timeWindow != null
                && newSlot.timeWindow != null
                && oldSlot.timeWindow.id.equals(newSlot.timeWindow.id)
                && oldSlot.slotDate.equals(newSlot.slotDate);
        if (!sameWindowAndDate) {
            validateWindowCapacity(newSlot);
        }

        validateSlotCapacity(newSlot);
        validateResourceNotInSlot(newSlot, resourceId);

        LOG.debugf("Move validation passed: slot %s -> %s, resource %s",
            oldSlot.id, newSlot.id, resourceId);
    }

    /**
     * Validate slot is open for bookings
     */
    private void validateSlotStatus(Slot slot) {
        validateSlotStatus(slot, false);
    }

    private void validateSlotStatus(Slot slot, boolean allowOverbooking) {
        if (slot.status == SlotStatus.CLOSED) {
            throw new BookingValidationException(
                "Slot is closed for bookings",
                "SLOT_CLOSED"
            );
        }
        if (slot.status == SlotStatus.OVERFLOW) {
            throw new BookingValidationException(
                "Slot was generated beyond the time window's bookable capacity",
                "SLOT_OVERFLOW"
            );
        }
        if (slot.status == SlotStatus.FULL && !allowOverbooking) {
            throw new BookingValidationException(
                "Slot is at full capacity",
                "SLOT_FULL"
            );
        }
    }

    /**
     * Validate the parent time window's total-capacity cap.
     *
     * <p>For MANUAL windows the slot grid is intentionally larger than the window's booking
     * capacity. The cap is on the <em>total</em> bookings across all of the window's slots for the
     * date — once that count is reached, no slot in the window accepts another booking, regardless
     * of order or of the target slot's own remaining room. AUTO windows are sized so that the slot
     * capacities sum exactly to the window capacity, so they need no extra check.
     */
    private void validateWindowCapacity(Slot slot) {
        TimeWindow tw = slot.timeWindow;
        if (tw == null || tw.kind != TimeWindowKind.WINDOW
                || tw.slotGenerationMode != SlotGenerationMode.MANUAL) {
            return;
        }
        long dayTotal = Booking.countByWindowAndDate(tw.id, slot.slotDate);
        if (dayTotal >= tw.capacity) {
            throw new BookingValidationException(
                String.format("Time window '%s' is full for %s (%d/%d)",
                    tw.name, slot.slotDate, dayTotal, tw.capacity),
                "WINDOW_CAPACITY_REACHED"
            );
        }
    }

    /**
     * Validate slot has available capacity
     */
    private void validateSlotCapacity(Slot slot) {
        if (slot.currentOccupancy >= slot.capacity) {
            throw new BookingValidationException(
                String.format("Slot has no available capacity (%d/%d)",
                    slot.currentOccupancy, slot.capacity),
                "NO_CAPACITY"
            );
        }
    }

    /**
     * Validate resource is not already booked in this slot
     */
    private void validateResourceNotInSlot(Slot slot, String resourceId) {
        Booking existing = Booking.findBySlotAndResource(slot.id, resourceId);
        if (existing != null) {
            throw new BookingValidationException(
                String.format("Resource '%s' is already booked in this slot", resourceId),
                "RESOURCE_ALREADY_BOOKED"
            );
        }
    }

    /**
     * Validate resource is not already booked on the same date
     * (Optional - uncomment in validateBooking if needed)
     */
    @SuppressWarnings("unused")
    private void validateResourceNotBookedOnDate(java.time.LocalDate date, String resourceId) {
        if (Booking.isResourceBookedOnDate(resourceId, date)) {
            throw new BookingValidationException(
                String.format("Resource '%s' is already booked on %s", resourceId, date),
                "RESOURCE_BOOKED_ON_DATE"
            );
        }
    }

    /**
     * Exception for booking validation failures
     */
    public static class BookingValidationException extends RuntimeException {
        private final String errorCode;

        public BookingValidationException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
