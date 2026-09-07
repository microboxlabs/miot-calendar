package com.microboxlabs.miot.calendar.validation;

import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.validation.BookingValidationService.BookingValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link BookingValidationService#validateBooking}. Slot status is checked before
 * anything that touches the database, so a plain instance suffices — no {@code @QuarkusTest} needed.
 */
class BookingValidationServiceTest {

    private final BookingValidationService validator = new BookingValidationService();

    private static Slot slotWithStatus(SlotStatus status) {
        Slot slot = new Slot();
        slot.status = status;
        slot.capacity = status == SlotStatus.OVERFLOW ? 0 : 1;
        slot.currentOccupancy = status == SlotStatus.FULL ? 1 : 0;
        return slot;
    }

    @Test
    void rejectsOverflowSlot() {
        Slot slot = slotWithStatus(SlotStatus.OVERFLOW);
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validateBooking(slot, "truck-1"));
        assertEquals("SLOT_OVERFLOW", ex.getErrorCode());
    }

    @Test
    void rejectsClosedSlot() {
        Slot slot = slotWithStatus(SlotStatus.CLOSED);
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validateBooking(slot, "truck-1"));
        assertEquals("SLOT_CLOSED", ex.getErrorCode());
    }

    @Test
    void overbookingStillRejectsOverflowSlot() {
        Slot slot = slotWithStatus(SlotStatus.OVERFLOW);
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validateBooking(slot, "truck-1", true));
        assertEquals("SLOT_OVERFLOW", ex.getErrorCode());
    }

    @Test
    void overbookingStillRejectsClosedSlot() {
        Slot slot = slotWithStatus(SlotStatus.CLOSED);
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validateBooking(slot, "truck-1", true));
        assertEquals("SLOT_CLOSED", ex.getErrorCode());
    }

    @Test
    void rejectsFullSlot() {
        Slot slot = slotWithStatus(SlotStatus.FULL);
        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> validator.validateBooking(slot, "truck-1"));
        assertEquals("SLOT_FULL", ex.getErrorCode());
    }
}
