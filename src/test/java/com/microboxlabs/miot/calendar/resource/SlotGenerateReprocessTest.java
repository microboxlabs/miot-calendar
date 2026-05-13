package com.microboxlabs.miot.calendar.resource;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.SlotGenerationMode;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@code reprocess} flag on {@code POST /slots/generate}: omitted/false leaves
 * existing rows alone (gap-fill semantics), {@code true} deletes unbooked rows and regenerates them
 * with the current config. The latter is the supported way to convert legacy {@code OVERFLOW}
 * rows (produced before the count-based window cap) to {@code OPEN} without a SQL migration.
 */
@QuarkusTest
class SlotGenerateReprocessTest {

    private static final String SLOTS_GENERATE_PATH = "/api/v1/miot-calendar/slots/generate";
    /** A future Monday — matches the window's daysOfWeek="1" and is >= validFrom (today). */
    private static final LocalDate A_MONDAY =
            LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    /**
     * Seed a calendar + MANUAL time window and persist a single legacy OVERFLOW slot row
     * (mimicking what an older deploy would have written). Wrapped in a TX so the seed is
     * flushed before the REST call runs.
     */
    @Transactional
    UUID seedCalendarWithLegacyOverflowSlot(String code, int hour) {
        Calendar cal = new Calendar();
        cal.code = code;
        cal.name = code;
        cal.parallelism = 2;
        cal.persist();

        TimeWindow tw = new TimeWindow();
        tw.calendar = cal;
        tw.name = "manual-window";
        tw.kind = TimeWindowKind.WINDOW;
        tw.slotGenerationMode = SlotGenerationMode.MANUAL;
        tw.startHour = 8;
        tw.endHour = 12;
        tw.slotDurationMinutes = 30;
        tw.capacity = 4;
        tw.daysOfWeek = "1";
        tw.validFrom = LocalDate.now();
        tw.active = true;
        tw.persist();

        Slot legacy = new Slot();
        legacy.calendar = cal;
        legacy.timeWindow = tw;
        legacy.slotDate = A_MONDAY;
        legacy.slotHour = hour;
        legacy.slotMinutes = 0;
        legacy.capacity = 0;
        legacy.currentOccupancy = 0;
        legacy.status = SlotStatus.OVERFLOW;
        legacy.persist();

        Panache.getEntityManager().flush();
        return cal.id;
    }

    @Test
    void reprocessFalseLeavesExistingOverflowRowAlone() {
        UUID calendarId = seedCalendarWithLegacyOverflowSlot("gen-reproc-skip", 9);

        // Default behaviour: no reprocess field. Existing slots are skipped; the legacy OVERFLOW
        // row at 09:00 is reported in slotsSkipped and survives untouched.
        given().contentType(ContentType.JSON)
            .body(String.format("""
                { "calendarId": "%s", "startDate": "%s", "endDate": "%s" }
                """, calendarId, A_MONDAY, A_MONDAY))
            .when().post(SLOTS_GENERATE_PATH)
            .then().statusCode(200)
            .body("slotsCreated", greaterThan(0))   // the other 7 slots get created
            .body("slotsSkipped", equalTo(1));      // the legacy 09:00 OVERFLOW row

        Slot at9 = Slot.findByCalendarAndDateTime(calendarId, A_MONDAY, 9, 0);
        assertEquals(SlotStatus.OVERFLOW, at9.status,
                "without reprocess, the legacy OVERFLOW row is preserved verbatim");
        assertEquals(0, at9.capacity);
    }

    @Test
    void reprocessTrueConvertsLegacyOverflowRowToOpen() {
        UUID calendarId = seedCalendarWithLegacyOverflowSlot("gen-reproc-fix", 10);

        // reprocess=true drops unbooked slots in range and regenerates them with the current
        // config — every MANUAL slot becomes OPEN with capacity = parallelism (2).
        given().contentType(ContentType.JSON)
            .body(String.format("""
                { "calendarId": "%s", "startDate": "%s", "endDate": "%s", "reprocess": true }
                """, calendarId, A_MONDAY, A_MONDAY))
            .when().post(SLOTS_GENERATE_PATH)
            .then().statusCode(200)
            .body("slotsCreated", equalTo(8));      // 240 min / 30 min = 8 slots, all regenerated

        Slot at10 = Slot.findByCalendarAndDateTime(calendarId, A_MONDAY, 10, 0);
        assertEquals(SlotStatus.OPEN, at10.status,
                "reprocess regenerated the legacy OVERFLOW row as OPEN");
        assertEquals(2, at10.capacity, "capacity = calendar parallelism");

        // Every slot in the window is now OPEN with capacity 2 — no leftover OVERFLOW.
        List<Slot> all = Slot.findByCalendarAndDateRange(calendarId, A_MONDAY, A_MONDAY);
        assertEquals(8, all.size());
        assertTrue(all.stream().allMatch(s -> s.status == SlotStatus.OPEN));
        assertTrue(all.stream().allMatch(s -> s.capacity == 2));
    }
}
