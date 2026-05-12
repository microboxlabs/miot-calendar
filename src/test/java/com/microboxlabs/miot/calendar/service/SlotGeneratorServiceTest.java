package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.SlotGenerationMode;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator-level tests for MANUAL slot generation: slots beyond the window's bookable quota are
 * emitted as {@link SlotStatus#OVERFLOW} (rendered, capacity 0, not bookable), AUTO behaviour is
 * unchanged, and a colliding BLOCK window overrides an unbooked OVERFLOW cell to CLOSED.
 *
 * <p>These exercise {@link SlotGeneratorService} directly (the REST layer can't configure a MANUAL
 * window until the time-window DTOs are wired). Each test runs inside a {@link TestTransaction} that
 * rolls back, so persisted fixtures never leak between tests.
 */
@QuarkusTest
class SlotGeneratorServiceTest {

    /** A future Monday — always >= validFrom (today) and matches daysOfWeek "1". */
    private static final LocalDate A_MONDAY =
            LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Inject
    SlotGeneratorService slotGeneratorService;

    private Calendar persistCalendar(String code, int parallelism) {
        Calendar c = new Calendar();
        c.code = code;
        c.name = code;
        c.parallelism = parallelism;
        c.persist();
        return c;
    }

    private TimeWindow persistWindow(Calendar calendar, TimeWindowKind kind, SlotGenerationMode mode,
                                     int startHour, int endHour, int slotDurationMinutes, int capacity) {
        TimeWindow tw = new TimeWindow();
        tw.calendar = calendar;
        tw.name = kind + "-" + mode;
        tw.kind = kind;
        tw.slotGenerationMode = mode;
        tw.startHour = startHour;
        tw.endHour = endHour;
        tw.slotDurationMinutes = slotDurationMinutes;
        tw.capacity = capacity;
        tw.daysOfWeek = "1";
        tw.validFrom = LocalDate.now();
        tw.active = true;
        tw.persist();
        return tw;
    }

    private List<Slot> generateAndFetch(UUID calendarId) {
        slotGeneratorService.generateSlots(calendarId, A_MONDAY, A_MONDAY);
        return Slot.findByCalendarAndDateRange(calendarId, A_MONDAY, A_MONDAY);
    }

    private long countByStatus(List<Slot> slots, SlotStatus status) {
        return slots.stream().filter(s -> s.status == status).count();
    }

    @Test
    @TestTransaction
    void manualWindowEmitsOpenSlotsThenOverflow() {
        // 4h window, 10-min slots → 24 slots fit; capacity 20, parallelism 1 → 20 OPEN + 4 OVERFLOW.
        Calendar cal = persistCalendar("gsvc-overflow", 1);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 10, 20);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(24, slots.size(), "floor(240min / 10min) slots");
        assertEquals(20, countByStatus(slots, SlotStatus.OPEN));
        assertEquals(4, countByStatus(slots, SlotStatus.OVERFLOW));
        assertEquals(0, countByStatus(slots, SlotStatus.CLOSED));
        // First 20 are OPEN with capacity 1; last 4 are OVERFLOW with capacity 0.
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (i < 20) {
                assertEquals(SlotStatus.OPEN, s.status, "slot " + i);
                assertEquals(1, s.capacity, "slot " + i + " capacity");
            } else {
                assertEquals(SlotStatus.OVERFLOW, s.status, "slot " + i);
                assertEquals(0, s.capacity, "slot " + i + " capacity");
            }
        }
        // First slot at 08:00, last at 08:00 + 23*10min = 11:50.
        assertEquals(8, slots.get(0).slotHour);
        assertEquals(0, slots.get(0).slotMinutes);
        assertEquals(11, slots.get(23).slotHour);
        assertEquals(50, slots.get(23).slotMinutes);
    }

    @Test
    @TestTransaction
    void manualWindowRespectsParallelismRemainderOnTheLastBookableSlot() {
        // 4h window, 30-min slots → 8 slots fit; capacity 7, parallelism 2 → ceil(7/2)=4 bookable
        // with capacities [2,2,2,1]; the remaining 4 slots are OVERFLOW.
        Calendar cal = persistCalendar("gsvc-remainder", 2);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 30, 7);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(8, slots.size());
        assertEquals(List.of(2, 2, 2, 1, 0, 0, 0, 0), slots.stream().map(s -> s.capacity).toList());
        assertEquals(4, countByStatus(slots, SlotStatus.OPEN));
        assertEquals(4, countByStatus(slots, SlotStatus.OVERFLOW));
        for (int i = 0; i < 4; i++) assertEquals(SlotStatus.OPEN, slots.get(i).status, "slot " + i);
        for (int i = 4; i < 8; i++) assertEquals(SlotStatus.OVERFLOW, slots.get(i).status, "slot " + i);
    }

    @Test
    @TestTransaction
    void autoWindowGeneratesOnlyBookableSlots() {
        // AUTO: numberOfSlots = ceil(capacity/parallelism) = 4, all bookable — no OVERFLOW.
        Calendar cal = persistCalendar("gsvc-auto", 1);
        // slotDurationMinutes mirrors what CalendarService would persist (240min / 4 = 60).
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.AUTO, 8, 12, 60, 4);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(4, slots.size());
        assertEquals(4, countByStatus(slots, SlotStatus.OPEN));
        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertTrue(slots.stream().allMatch(s -> s.capacity == 1));
    }

    @Test
    @TestTransaction
    void blockWindowOverridesUnbookedOverflowCells() {
        // WINDOW (MANUAL) 08-12, 60-min slots, capacity 2 → @8 OPEN, @9 OPEN, @10 OVERFLOW, @11 OVERFLOW.
        // BLOCK 10-12 then overrides the two unbooked OVERFLOW cells → CLOSED (and adds CLOSED cells
        // on the 30-min block grid).
        Calendar cal = persistCalendar("gsvc-block", 1);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 60, 2);
        persistWindow(cal, TimeWindowKind.BLOCK, SlotGenerationMode.MANUAL, 10, 12, 30, 0);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW), "BLOCK should override OVERFLOW cells");
        assertEquals(2, countByStatus(slots, SlotStatus.OPEN));
        assertTrue(countByStatus(slots, SlotStatus.CLOSED) >= 2, "the two 10:00/11:00 cells became CLOSED");
        // The 08:00 and 09:00 slots stay OPEN.
        assertEquals(SlotStatus.OPEN, slots.get(0).status);
        assertEquals(8, slots.get(0).slotHour);
        assertEquals(SlotStatus.OPEN, slots.get(1).status);
        assertEquals(9, slots.get(1).slotHour);
        // Everything from 10:00 on is CLOSED.
        slots.stream()
                .filter(s -> s.slotHour >= 10)
                .forEach(s -> assertEquals(SlotStatus.CLOSED, s.status, "slot at " + s.slotHour + ":" + s.slotMinutes));
    }
}
