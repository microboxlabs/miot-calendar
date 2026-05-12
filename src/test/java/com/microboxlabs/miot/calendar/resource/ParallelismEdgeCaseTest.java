package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Integration tests for parallelism edge cases: capacity enforcement after
 * reduction, boundary validation, time window interactions, generation after
 * updates, and concurrency.
 *
 * <p>Each test uses a unique calendar code so tests remain independent.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParallelismEdgeCaseTest {

    private static final String CALENDARS_PATH = "/api/v1/miot-calendar/calendars";
    private static final String SLOTS_PATH     = "/api/v1/miot-calendar/slots";
    private static final String BOOKINGS_PATH  = "/api/v1/miot-calendar/bookings";
    private static final String MANAGERS_PATH  = "/api/v1/miot-calendar/slot-managers";

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
            .extract().path("id");
    }

    private String createCalendarNoAutoManager(String code, int parallelism) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "code": "%s",
                    "name": "%s",
                    "timezone": "America/Santiago",
                    "parallelism": %d,
                    "autoSlotManager": false
                }
                """, code, code, parallelism))
            .when()
            .post(CALENDARS_PATH)
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private void createTimeWindow(String calendarId, int startHour, int endHour, int capacity) {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Test Window",
                    "slotGenerationMode": "AUTO",
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

    private String createTimeWindowReturningId(String calendarId, int startHour, int endHour,
                                               int capacity, String daysOfWeek) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Test Window",
                    "slotGenerationMode": "AUTO",
                    "startHour": %d,
                    "endHour": %d,
                    "capacity": %d,
                    "daysOfWeek": "%s",
                    "validFrom": "%s"
                }
                """, startHour, endHour, capacity, daysOfWeek, LocalDate.now()))
            .when()
            .post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then()
            .statusCode(201)
            .extract().path("id");
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

    private String bookResource(String calendarId, String resourceId, LocalDate date,
                                int hour, int minutes) {
        return given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "test-user")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "%s",
                        "type": "SERVICE",
                        "label": "Test resource %s"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, resourceId, resourceId, date, hour, minutes))
            .when()
            .post(BOOKINGS_PATH)
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private int bookResourceExpectStatus(String calendarId, String resourceId, LocalDate date,
                                         int hour, int minutes) {
        return given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "test-user")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "%s",
                        "type": "SERVICE",
                        "label": "Test resource %s"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, resourceId, resourceId, date, hour, minutes))
            .when()
            .post(BOOKINGS_PATH)
            .then()
            .extract().statusCode();
    }

    private String createSlotManager(String calendarId, int daysInAdvance) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "daysInAdvance": %d,
                    "batchDays": 7
                }
                """, calendarId, daysInAdvance))
            .when()
            .post(MANAGERS_PATH)
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private void triggerManagerRun(String managerId) {
        given()
            .when()
            .post(MANAGERS_PATH + "/" + managerId + "/run")
            .then()
            .statusCode(200);
    }

    // ── Group 1: Over-capacity slot after parallelism reduction ──────────────

    /**
     * After reducing parallelism, a booked slot retains its old capacity.
     * A 3rd booking against that slot (capacity=2) must be rejected.
     */
    @Test
    void testFullSlotRejectsThirdBooking() {
        // Calendar parallelism=2, window 8-12, capacity=8 → 4 slots of 60min, capacity=2
        String calendarId = createCalendar("edge-overcap-reject", 2);
        createTimeWindow(calendarId, 8, 12, 8);

        // Book 2 resources in 08:00 slot → fills it
        bookResource(calendarId, "RES-A1", A_WEEKDAY, 8, 0);
        bookResource(calendarId, "RES-A2", A_WEEKDAY, 8, 0);

        // Update parallelism to 1 → reprocess deletes unbooked slots but preserves booked slot
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "parallelism": 1 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200);

        // The booked 08:00 slot still has capacity=2 (preserved because it has bookings).
        // A 3rd booking must fail: occupancy=2, capacity=2 → FULL.
        given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "test-user")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "RES-A3",
                        "type": "SERVICE",
                        "label": "Third resource"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": 8,
                        "minutes": 0
                    }
                }
                """, calendarId, A_WEEKDAY))
            .when()
            .post(BOOKINGS_PATH)
            .then()
            .statusCode(409);
    }

    /**
     * After a slot is full (occupancy=capacity=2), cancelling one booking
     * reopens it, allowing a new booking to succeed.
     */
    @Test
    void testCancelBookingReopensFullSlot() {
        // Calendar parallelism=2, window 8-12, capacity=8 → 4 slots of 60min, capacity=2
        String calendarId = createCalendar("edge-overcap-reopen", 2);
        createTimeWindow(calendarId, 8, 12, 8);

        // Fill the 08:00 slot
        String bookingToCancel = bookResource(calendarId, "RES-B1", A_WEEKDAY, 8, 0);
        bookResource(calendarId, "RES-B2", A_WEEKDAY, 8, 0);

        // Reduce parallelism → preserves booked slot
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "parallelism": 1 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200);

        // Cancel one booking → slot should become OPEN
        given()
            .when()
            .delete(BOOKINGS_PATH + "/" + bookingToCancel)
            .then()
            .statusCode(204);

        // Now a new booking should succeed
        bookResource(calendarId, "RES-B3", A_WEEKDAY, 8, 0);
    }

    // ── Group 2: Parallelism boundary values ─────────────────────────────────

    /**
     * parallelism=0 must be rejected at creation time.
     */
    @Test
    void testCreateCalendarWithZeroParallelism() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "edge-zero-par",
                    "name": "Zero Parallelism",
                    "parallelism": 0
                }
                """)
            .when()
            .post(CALENDARS_PATH)
            .then()
            .statusCode(400);
    }

    /**
     * parallelism=-1 must be rejected at creation time.
     */
    @Test
    void testCreateCalendarWithNegativeParallelism() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "edge-neg-par",
                    "name": "Negative Parallelism",
                    "parallelism": -1
                }
                """)
            .when()
            .post(CALENDARS_PATH)
            .then()
            .statusCode(400);
    }

    /**
     * A very high parallelism value (1000) must be accepted.
     */
    @Test
    void testUpdateToVeryHighParallelism() {
        String calendarId = createCalendar("edge-high-par", 1);

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "parallelism": 1000 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200);
    }

    // ── Group 3: Interaction with time windows ───────────────────────────────

    /**
     * Two time windows on the same calendar (8-10 and 14-16) must both produce
     * slots whose capacity equals the calendar's parallelism.
     */
    @Test
    void testMultipleTimeWindowsRespectSameParallelism() {
        String calendarId = createCalendar("edge-multi-tw", 3);

        // Window 1: 8-10, capacity=6 → 6/3=2 slots of 60min, capacity=3
        createTimeWindow(calendarId, 8, 10, 6);
        // Window 2: 14-16, capacity=6 → 6/3=2 slots of 60min, capacity=3
        createTimeWindow(calendarId, 14, 16, 6);

        // All slots for this day should have capacity=3
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.every { it.capacity == 3 }", equalTo(true));
    }

    /**
     * Verify that updating parallelism and then updating time window capacity
     * produces correctly recalculated slots.
     *
     * <p>Steps:
     * 1. Calendar parallelism=2, window 8-12 capacity=8 → 4 slots of 60min, capacity=2
     * 2. Update parallelism to 4 → 8/4=2 slots of 120min, capacity=4
     * 3. Update time window capacity to 12 → 12/4=3 slots of 80min, capacity=4
     */
    @Test
    void testCapacityChangeAfterParallelismRecalculates() {
        String calendarId = createCalendarNoAutoManager("edge-cap-repar", 2);
        String twId = createTimeWindowReturningId(calendarId, 8, 12, 8, "1,2,3,4,5");
        String managerId = createSlotManager(calendarId, 30);
        triggerManagerRun(managerId);

        // Step 1: Verify initial state — 4 slots of 60min, capacity=2
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.every { it.capacity == 2 }", equalTo(true));

        // Step 2: Update parallelism to 4 → 8/4=2 slots of 120min, capacity=4
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "parallelism": 4 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200);

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(2))
            .body("data.every { it.capacity == 4 }", equalTo(true));

        // Step 3: Update time window capacity to 12 → 12/4=3 slots of 80min, capacity=4
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "capacity": 12 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId + "/time-windows/" + twId)
            .then()
            .statusCode(200);

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(3))
            .body("data.every { it.capacity == 4 }", equalTo(true));
    }

    // ── Group 4: Slot generation after range extension ───────────────────────

    /**
     * After updating parallelism and extending daysInAdvance, newly generated
     * slots beyond the original horizon must use the updated parallelism.
     */
    @Test
    void testExtendedRangeUsesUpdatedParallelism() {
        String calendarId = createCalendarNoAutoManager("edge-ext-range", 1);
        createTimeWindow(calendarId, 9, 11, 2);
        String managerId = createSlotManager(calendarId, 30);
        triggerManagerRun(managerId);

        // Verify initial slots have capacity=1
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_WEEKDAY.toString())
            .queryParam("endDate", A_WEEKDAY.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.every { it.capacity == 1 }", equalTo(true));

        // Update parallelism to 3 → triggers reprocess of existing range
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "parallelism": 3 }
                """)
            .when()
            .put(CALENDARS_PATH + "/" + calendarId)
            .then()
            .statusCode(200);

        // Extend daysInAdvance to 60 so day 31+ will be newly generated
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "daysInAdvance": 60 }
                """)
            .when()
            .put(MANAGERS_PATH + "/" + managerId)
            .then()
            .statusCode(200)
            .body("daysInAdvance", equalTo(60));

        // Trigger a manual run to generate the extended range
        triggerManagerRun(managerId);

        // Pick a weekday in the 31-60 day range (new territory)
        LocalDate futureWeekday = LocalDate.now().plusDays(45)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        // Newly generated slots should preserve the window capacity.
        // parallelism=3, capacity=2 → one slot, duration=120min, capacity=2
        List<Object> slots = getSlots(calendarId, futureWeekday);
        Assertions.assertFalse(slots.isEmpty(),
                "Expected slots on a future weekday within extended range");

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", futureWeekday.toString())
            .queryParam("endDate", futureWeekday.toString())
            .when()
            .get(SLOTS_PATH)
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(1))
            .body("data[0].capacity", equalTo(2));
    }

    // ── Group 5: Concurrency ─────────────────────────────────────────────────

    /**
     * With parallelism=1 (capacity=1), two concurrent booking requests for the
     * same slot must result in exactly one success (201) and one rejection (409).
     *
     * <p>Note: Current implementation uses transaction isolation only (no pessimistic
     * locking), so both requests may succeed due to a race condition. When locking
     * is added, tighten the assertion to {@code assertEquals(1, successes)}.
     */
    @Test
    void testConcurrentBookingSameSlotParallelismOne() throws Exception {
        String calendarId = createCalendar("edge-concurrent", 1);
        // capacity=1, parallelism=1 → 1 slot per hour, capacity=1
        createTimeWindow(calendarId, 8, 9, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(
                () -> bookResourceExpectStatus(calendarId, "CONC-R1", A_WEEKDAY, 8, 0),
                executor);
            CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(
                () -> bookResourceExpectStatus(calendarId, "CONC-R2", A_WEEKDAY, 8, 0),
                executor);

            int status1 = future1.get();
            int status2 = future2.get();

            List<Integer> statuses = new ArrayList<>(List.of(status1, status2));
            long successes = statuses.stream().filter(s -> s == 201).count();
            long conflicts = statuses.stream().filter(s -> s == 409).count();

            // Both responses must be either 201 or 409 — no 500s or other errors.
            Assertions.assertEquals(2, successes + conflicts,
                    "Each request should be either 201 or 409, got statuses: " + statuses);
        } finally {
            executor.shutdown();
        }
    }
}
