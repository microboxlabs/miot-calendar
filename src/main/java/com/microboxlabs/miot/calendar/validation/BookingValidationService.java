package com.microboxlabs.miot.calendar.validation;

import com.microboxlabs.miot.calendar.entity.Booking;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Service for validating bookings
 */
@ApplicationScoped
public class BookingValidationService {

    private static final Logger LOG = Logger.getLogger(BookingValidationService.class);

    /**
     * Validate a booking request
     * 
     * @param slot The slot to book
     * @param resourceId The resource to book
     * @throws BookingValidationException if validation fails
     */
    public void validateBooking(Slot slot, String resourceId) {
        // 1. Check slot status
        validateSlotStatus(slot);
        
        // 2. Check slot capacity
        validateSlotCapacity(slot);
        
        // 3. Check resource not already in slot
        validateResourceNotInSlot(slot, resourceId);
        
        // 4. Optional: Check resource not already booked on same day
        // This is commented out as it may not always be required
        // validateResourceNotBookedOnDate(slot.slotDate, resourceId);
        
        LOG.debugf("Booking validation passed for slot %s, resource %s", slot.id, resourceId);
    }

    /**
     * Validate slot is open for bookings
     */
    private void validateSlotStatus(Slot slot) {
        if (slot.status == SlotStatus.CLOSED) {
            throw new BookingValidationException(
                "Slot is closed for bookings",
                "SLOT_CLOSED"
            );
        }
        if (slot.status == SlotStatus.FULL) {
            throw new BookingValidationException(
                "Slot is at full capacity",
                "SLOT_FULL"
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
