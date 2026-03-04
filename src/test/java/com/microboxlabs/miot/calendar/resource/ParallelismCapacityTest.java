package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the parallelism/capacity model introduced in v0.4.0.
 *
 * <p>Covers two bugs fixed together:
 *
 * <ol>
 *   <li><b>Arbitrary slot minutes</b>: The DB constraint {@code cld_slots_slot_minutes_check}
 *       only allowed values IN (0, 30). The new capacity model derives arbitrary
 *       durations (e.g., 12 min), producing minute offsets like 12, 24, 36… that
 *       violated the constraint. Fixed by V8 migration relaxing to BETWEEN 0 AND 59.</li>
 *
 *   <li><b>Reprocess after parallelism change</b>: Updating a calendar's parallelism
 *       set reprocessFrom/To on the SlotManager, but the generator only skipped
 *       existing slots — never updated them. Fixed by purging unbooked slots
 *       before regenerating during a reprocess run.</li>
 * </ol>
 *
 * <p>Each test uses a unique calendar code so tests remain independent.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParallelismCapacityTest {

    private static final String CALENDARS_PATH = "/api/v1/miot-calendar/calendars";
    private static final String SLOTS_PATH     = "/api/v1/miot-calendar/slots";

    /** Pick a future weekday so slots are always generated (daysOfWeek 1-5). */
    private static final LocalDate A_WEEKDAY =
            LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createCalendar(String code, int parallelism) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "code": "%s",
                    "name": "%s",
                    "timezone": "America/Santiago",
                    "parallelism": %d
                }
                """, code, code, parallelism))
            .when()
            .post(CALENDARS_PATH)
            .then()
            .statusCode(201)
            .body("parallelism", equalTo(parallelism))
            .extract().path("id");
    }

    private void createTimeWindow(String calendarId, int startHour, int endHour, int capacity) {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Test Window",
                    "startHour": %d,
                    "endHour": %d,
                    "capacity": %d,
                    "daysOfWeek": "1,2,3,4,5",
                    "validFrom": "%s"
                }
                """, startHour, endHour, capacity, LocalDate.now()))
            .when()
            .post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);
    }

    private List<Object> getSlots(String calendarId, LocalDate date) {
        return given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", date.toString())
            .queryParam("endDate", date.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .extract().path("data");
    }

    // ── Bug 1: Arbitrary slot minutes (V8 constraint fix) ────────────────────

    /**
     * A 4-hour window (08:00–12:00) with capacity=20 and parallelism=1
     * yields slotDuration = 240 / 20 = 12 minutes.
     *
     * <p>Before V8: the DB CHECK constraint {@code slot_minutes IN (0, 30)}
     * rejected slots at minutes 12, 24, 36, 48.
     * After V8: the constraint allows BETWEEN 0 AND 59.
     */
    @Test
    void testArbitrarySlotMinutesAreAccepted() {
        String calendarId = createCalendar("par-arb-minutes", 1);
        // capacity=20, window=240min, parallelism=1 → 20 slots of 12min each
        createTimeWindow(calendarId, 8, 12, 20);

        List<Object> slots = getSlots(calendarId, A_WEEKDAY);

        // 240min / 12min = 20 slots per day
        Assertions.assertEquals(20, slots.size(),
            "Expected 20 slots of 12min in a 4-hour window with capacity=20, parallelism=1");
    }

    /**
     * Verify that the 12-minute slots produce the correct minute offsets:
     * 0, 12, 24, 36, 48, 0, 12, 24, ... across hours 8–11.
     */
    @Test
    void testArbitrarySlotMinuteOffsets() {
        String calendarId = createCalendar("par-arb-offsets", 1);
        // capacity=20, window=240min → 20 slots of 12min
        createTimeWindow(calendarId, 8, 12, 20);

        // Check minute values for first hour (08:xx) — should be 0, 12, 24, 36, 48
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.findAll { it.slotHour == 8 }.slotMinutes",
                containsInAnyOrder(0, 12, 24, 36, 48));
    }

    // ── Bug 2: Reprocess after parallelism change ────────────────────────────

    /**
     * Changing a calendar's parallelism must trigger slot regeneration with the
     * new capacity value.
     *
     * <p>Before the fix: the slot manager set reprocessFrom/To but the generator
     * only skipped existing slots — never updated their capacity. The manual
     * re-run returned SKIPPED.
     *
     * <p>After the fix: reprocess deletes unbooked slots and regenerates them
     * with the new parallelism-derived capacity.
     */
    @Test
    void testParallelismChangeRegeneratesSlots() {
        // Step 1: Create calendar with parallelism=1
        String calendarId = createCalendar("par-regen", 1);
        // capacity=4, window=240min, parallelism=1 → 4 slots of 60min, each with capacity=1
        createTimeWindow(calendarId, 8, 12, 4);

        // Verify slots have capacity=1
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.every { it.capacity == 1 }", equalTo(true));

        // Step 2: Update parallelism to 2
        // capacity=4, parallelism=2 → 2 slots of 120min, each with capacity=2
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "parallelism": 2
                }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200)
            .body("parallelism", equalTo(2));

        // Step 3: Verify slots were regenerated with new capacity
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(2))
            .body("data.every { it.capacity == 2 }", equalTo(true));
    }

    /**
     * Verify the full parallelism example from the plan:
     * 5 docks, 20 trucks, 4-hour window → 4 slots of 60min, each with capacity=5.
     */
    @Test
    void testParallelismExampleFiveDockstwentyTrucks() {
        String calendarId = createCalendar("par-5dock-20truck", 5);
        createTimeWindow(calendarId, 8, 12, 20);

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.every { it.capacity == 5 }", equalTo(true));
    }

    /**
     * Verify floor behavior: parallelism=3, capacity=10, 4-hour window.
     * numberOfSlots = 10 / 3 = 3 (integer division)
     * slotDuration  = 240 / 3 = 80min
     * slot.capacity = 3
     * actualTotal   = 3 * 3 = 9 (≤ requested 10)
     */
    @Test
    void testFloorBehaviorWithUnevenDivision() {
        String calendarId = createCalendar("par-floor-div", 3);
        createTimeWindow(calendarId, 8, 12, 10);

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(3))
            .body("data.every { it.capacity == 3 }", equalTo(true));
    }

    /**
     * Default parallelism=1 with capacity=1 in a 1-hour window must produce
     * exactly one 60-min slot with capacity=1 — backward-compatible behavior.
     */
    @Test
    void testDefaultParallelismBackwardCompatible() {
        String calendarId = createCalendar("par-default-compat", 1);
        createTimeWindow(calendarId, 9, 10, 1);

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(1))
            .body("data[0].capacity", equalTo(1));
    }
}
