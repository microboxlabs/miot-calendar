package com.microboxlabs.miot.calendar.entity;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BookingResourceSearchTest {

    @Test
    @TestTransaction
    void resourceSearchIsCappedAndPreservesOrdering() {
        Calendar calendar = new Calendar();
        calendar.code = "resource-search-" + UUID.randomUUID().toString().substring(0, 8);
        calendar.name = "Resource search";
        calendar.persist();

        LocalDate firstDate = LocalDate.of(2020, 1, 1);
        for (int index = 0; index <= Booking.RESOURCE_SEARCH_MAX_RESULTS; index++) {
            Slot slot = new Slot();
            slot.calendar = calendar;
            slot.slotDate = firstDate.plusDays(index);
            slot.slotHour = 9;
            slot.slotMinutes = 0;
            slot.capacity = 1;
            slot.persist();

            Booking booking = new Booking();
            booking.slot = slot;
            booking.calendar = calendar;
            booking.resourceId = "RESOURCE-CAP-" + index;
            booking.persist();
        }

        List<Booking> results = Booking.findByResourceIdContaining(
            calendar.id, null, null, null, "resource-cap");

        assertEquals(Booking.RESOURCE_SEARCH_MAX_RESULTS, results.size());
        assertEquals(firstDate, results.getFirst().slotDate);
        assertEquals(
            firstDate.plusDays(Booking.RESOURCE_SEARCH_MAX_RESULTS - 1),
            results.getLast().slotDate);
    }
}
