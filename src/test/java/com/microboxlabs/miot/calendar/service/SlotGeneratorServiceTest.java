package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.SlotGenerationMode;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import io.quarkus.hibernate.orm.panache.Panache;
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
 * Generator-level tests for slot generation. MANUAL windows emit {@code floor(windowMinutes /
 * slotDurationMinutes)} slots, all {@code OPEN}, each carrying {@code parallelism} capacity — the
 * window's total-capacity cap is enforced at booking time, not by withholding slots. AUTO behaviour
 * is unchanged, and a colliding BLOCK window still overrides an unbooked OPEN cell to CLOSED.
 *
 * <p>These exercise {@link SlotGeneratorService} directly. Each test runs inside a
 * {@link TestTransaction} that rolls back, so persisted fixtures never leak between tests.
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
    void manualWindowEmitsEverySlotOpen() {
        // 4h window, 10-min slots → 24 slots fit; capacity 20, parallelism 1 → all 24 OPEN, capacity 1.
        Calendar cal = persistCalendar("gsvc-manual", 1);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 10, 20);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(24, slots.size(), "floor(240min / 10min) slots");
        assertEquals(24, countByStatus(slots, SlotStatus.OPEN));
        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertEquals(0, countByStatus(slots, SlotStatus.CLOSED));
        assertTrue(slots.stream().allMatch(s -> s.capacity == 1), "every MANUAL slot carries parallelism (1)");
        // First slot at 08:00, last at 08:00 + 23*10min = 11:50.
        assertEquals(8, slots.get(0).slotHour);
        assertEquals(0, slots.get(0).slotMinutes);
        assertEquals(11, slots.get(23).slotHour);
        assertEquals(50, slots.get(23).slotMinutes);
    }

    @Test
    @TestTransaction
    void manualSlotCapacityEqualsParallelism() {
        // 4h window, 30-min slots → 8 slots fit; capacity 7, parallelism 2 → all 8 OPEN, capacity 2.
        // (The window cap of 7 is enforced at booking time, not by shrinking per-slot capacity.)
        Calendar cal = persistCalendar("gsvc-parallelism", 2);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 30, 7);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(8, slots.size());
        assertEquals(8, countByStatus(slots, SlotStatus.OPEN));
        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertTrue(slots.stream().allMatch(s -> s.capacity == 2));
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
    void autoWindowFrontLoadsParallelismRemainderOnTheLastSlot() {
        // AUTO 4h window, capacity 7, parallelism 2 → ceil(7/2)=4 slots with capacities [2,2,2,1].
        Calendar cal = persistCalendar("gsvc-auto-remainder", 2);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.AUTO, 8, 12, 60, 7);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(4, slots.size());
        assertEquals(List.of(2, 2, 2, 1), slots.stream().map(s -> s.capacity).toList());
        assertTrue(slots.stream().allMatch(s -> s.status == SlotStatus.OPEN));
    }

    @Test
    @TestTransaction
    void blockWindowOverridesUnbookedOpenCells() {
        // WINDOW (MANUAL) 08-12, 60-min slots, capacity 2 → four OPEN cells @8/@9/@10/@11.
        // BLOCK 10-12 then overrides the two unbooked 10:00/11:00 cells → CLOSED (and adds CLOSED
        // cells on the 30-min block grid).
        Calendar cal = persistCalendar("gsvc-block", 1);
        persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 60, 2);
        persistWindow(cal, TimeWindowKind.BLOCK, SlotGenerationMode.MANUAL, 10, 12, 30, 0);

        List<Slot> slots = generateAndFetch(cal.id);

        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertEquals(2, countByStatus(slots, SlotStatus.OPEN), "08:00 and 09:00 stay OPEN");
        assertTrue(countByStatus(slots, SlotStatus.CLOSED) >= 2, "the two 10:00/11:00 cells became CLOSED");
        assertEquals(SlotStatus.OPEN, slots.get(0).status);
        assertEquals(8, slots.get(0).slotHour);
        assertEquals(SlotStatus.OPEN, slots.get(1).status);
        assertEquals(9, slots.get(1).slotHour);
        // Everything from 10:00 on is CLOSED.
        slots.stream()
                .filter(s -> s.slotHour >= 10)
                .forEach(s -> assertEquals(SlotStatus.CLOSED, s.status, "slot at " + s.slotHour + ":" + s.slotMinutes));
    }

    @Test
    @TestTransaction
    void bookedSlotSurvivesReprocessAfterCapacityDrop() {
        // 4h window, 30-min slots → 8 slots; capacity 8, parallelism 1 → all 8 OPEN.
        Calendar cal = persistCalendar("gsvc-reproc", 1);
        TimeWindow tw = persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 30, 8);
        slotGeneratorService.generateSlots(cal.id, A_MONDAY, A_MONDAY);

        // Simulate a booking on the 08:00 slot, then drop the window capacity to 2.
        Slot first = Slot.findByCalendarAndDateTime(cal.id, A_MONDAY, 8, 0);
        first.currentOccupancy = 1;
        tw.capacity = 2;
        Panache.getEntityManager().flush();
        Panache.getEntityManager().clear();

        slotGeneratorService.generateSlots(cal.id, A_MONDAY, A_MONDAY, true); // reprocess: drop unbooked, regenerate

        List<Slot> slots = Slot.findByCalendarAndDateRange(cal.id, A_MONDAY, A_MONDAY);
        assertEquals(8, slots.size(), "8 slots still fit the window regardless of the lowered capacity cap");
        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertTrue(slots.stream().allMatch(s -> s.status == SlotStatus.OPEN));
        Slot surviving = slots.stream()
                .filter(s -> s.slotHour == 8 && s.slotMinutes == 0)
                .findFirst().orElseThrow();
        assertEquals(1, surviving.currentOccupancy, "the booked slot is preserved across the reprocess");
    }

    @Test
    @TestTransaction
    void manualToAutoReprocessShrinksSlotGrid() {
        // MANUAL 4h/10-min/cap20/p1 → 24 OPEN slots.
        Calendar cal = persistCalendar("gsvc-m2a", 1);
        TimeWindow tw = persistWindow(cal, TimeWindowKind.WINDOW, SlotGenerationMode.MANUAL, 8, 12, 10, 20);
        slotGeneratorService.generateSlots(cal.id, A_MONDAY, A_MONDAY);
        assertEquals(24, Slot.findByCalendarAndDateRange(cal.id, A_MONDAY, A_MONDAY).size());

        // Switch to AUTO (mimic CalendarService: re-derive the duration), then reprocess.
        tw.slotGenerationMode = SlotGenerationMode.AUTO;
        tw.slotDurationMinutes = tw.computeSlotDurationMinutes(); // 240 / ceil(20/1) = 12
        Panache.getEntityManager().flush();
        Panache.getEntityManager().clear();

        slotGeneratorService.generateSlots(cal.id, A_MONDAY, A_MONDAY, true);

        List<Slot> slots = Slot.findByCalendarAndDateRange(cal.id, A_MONDAY, A_MONDAY);
        assertEquals(20, slots.size(), "AUTO sizes the grid to ceil(capacity/parallelism)");
        assertEquals(0, countByStatus(slots, SlotStatus.OVERFLOW));
        assertTrue(slots.stream().allMatch(s -> s.status == SlotStatus.OPEN));
    }
}
