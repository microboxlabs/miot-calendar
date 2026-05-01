package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for slot generation correctness.
 *
 * Covers two bugs fixed together:
 *
 *   Bug 1 – day-of-week filter was broken:
 *     The API schema described numeric codes ("1,2,3,4,5") while the generator
 *     produced 3-letter codes ("MON"…"SUN"). includesDay() used String.contains(),
 *     so "1,2,3,4,5".contains("MON") == false — every date was silently skipped
 *     and 0 slots were ever created.
 *
 *   Bug 2 – endHour was treated as inclusive:
 *     The while-loop condition `hour < endHour || (hour == endHour && minutes == 0)`
 *     emitted one extra slot AT endHour. For an 08:00–12:00 window with 60-min
 *     slots the generator created 5 slots (8,9,10,11,12) instead of the documented
 *     4 (8,9,10,11).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotGenerationTest {

    private static final String SLOTS_GENERATE_BODY = """
            {
                "calendarId": "%s",
                "startDate": "%s",
                "endDate":   "%s"
            }
            """;
    private static final String SLOTS_CREATED  = "slotsCreated";
    private static final String SLOTS_SKIPPED  = "slotsSkipped";

    @TestHTTPResource
    URL url;

    @TestHTTPEndpoint(SlotResource.class)
    @TestHTTPResource("generate")
    URL slotsGenerateUrl;

    private String calendarId;

    // Use a fixed Sunday and the following Monday so that both dates are
    // always >= today (validFrom) regardless of which day the suite runs.
    private static final LocalDate A_SUNDAY =
            LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    private static final LocalDate A_MONDAY =
            LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    @Test
    @Order(0)
    void setupCalendarWithWeekdayOnlyWindow() {
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "slot-gen-test",
                    "name": "Slot Generation Test Calendar",
                    "autoSlotManager": false
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Weekdays only, 08:00–12:00 (exclusive end), 60-min slots
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Weekday Morning",
                    "startHour": 8,
                    "endHour": 12,
                    "capacity": 4,
                    "daysOfWeek": "1,2,3,4,5",
                    "validFrom": "%s"
                }
                """, LocalDate.now()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);
    }

    // ── Bug 1: day-of-week filter ─────────────────────────────────────────

    /**
     * A MON–FRI window must produce zero slots for a Sunday.
     * Before the fix, includesDay() used String.contains() with 3-letter codes
     * against numeric values ("1,2,3,4,5") so it silently returned false for
     * every day, yielding 0 slots even on weekdays.
     */
    @Test
    @Order(1)
    void weekdayWindowProducesZeroSlotsOnSunday() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format(SLOTS_GENERATE_BODY, calendarId, A_SUNDAY, A_SUNDAY))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200)
            .body(SLOTS_CREATED, equalTo(0));
    }

    /**
     * The same MON–FRI window must generate slots on a Monday.
     * This verifies that the day-of-week filter correctly accepts weekdays.
     */
    @Test
    @Order(2)
    void weekdayWindowProducesSlotsOnMonday() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format(SLOTS_GENERATE_BODY, calendarId, A_MONDAY, A_MONDAY))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200)
            .body(SLOTS_CREATED, greaterThan(0));
    }

    // ── Bug 2: exclusive endHour ──────────────────────────────────────────

    /**
     * An 08:00–12:00 window with 60-min slots must produce exactly 4 slots
     * per day: 08:00, 09:00, 10:00, 11:00.
     * Before the fix the loop condition `hour == endHour && minutes == 0`
     * emitted a fifth slot at 12:00.
     *
     * The Monday was already generated by the previous test; re-generating
     * must report slotsCreated=0 and slotsSkipped=4, confirming the exact count.
     */
    @Test
    @Order(3)
    void slotCountPerDayMatchesExclusiveEndHour() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format(SLOTS_GENERATE_BODY, calendarId, A_MONDAY, A_MONDAY))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200)
            .body(SLOTS_CREATED, equalTo(0))
            .body(SLOTS_SKIPPED, equalTo(4));   // exactly 4 slots exist, none at endHour
    }

    /**
     * No slot at 12:00 must appear in the stored list for Monday.
     * The four stored slots must be 08:00, 09:00, 10:00, 11:00.
     */
    @Test
    @Order(4)
    void noSlotExistsAtEndHour() {
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_MONDAY.toString())
            .queryParam("endDate",   A_MONDAY.toString())
            .when()
            .get("/api/v1/miot-calendar/slots")
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.slotHour", everyItem(not(equalTo(12))))
            .body("data.slotHour", containsInAnyOrder(8, 9, 10, 11));
    }

    // ── BLOCK windows: temporal blockades persist as CLOSED slots ─────────

    /**
     * A BLOCK window is created without capacity and overlays the existing
     * 08:00–12:00 WINDOW on the same Monday. After regeneration, the slots
     * inside the block range must be CLOSED so the planning UI can paint
     * them as blocked.
     */
    @Test
    @Order(5)
    void blockWindowProducesClosedSlots() {
        // BLOCK 09:00–11:00 on weekdays — overlaps the 08:00–12:00 WINDOW.
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Maintenance",
                    "kind": "BLOCK",
                    "startHour": 9,
                    "endHour": 11,
                    "daysOfWeek": "1,2,3,4,5",
                    "validFrom": "%s"
                }
                """, LocalDate.now()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201)
            .body("kind", equalTo("BLOCK"));

        // Re-run slot generation on Monday so the BLOCK overlays the existing
        // OPEN slots (overwrite-on-collision in SlotGeneratorService).
        given()
            .contentType(ContentType.JSON)
            .body(String.format(SLOTS_GENERATE_BODY, calendarId, A_MONDAY, A_MONDAY))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200);

        // Slots at 09 and 10 must be CLOSED; 08 and 11 must remain OPEN.
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", A_MONDAY.toString())
            .queryParam("endDate",   A_MONDAY.toString())
            .when()
            .get("/api/v1/miot-calendar/slots")
            .then()
            .statusCode(200)
            .body("data.find { it.slotHour == 8 }.status",  equalTo("OPEN"))
            .body("data.find { it.slotHour == 9 }.status",  equalTo("CLOSED"))
            .body("data.find { it.slotHour == 10 }.status", equalTo("CLOSED"))
            .body("data.find { it.slotHour == 11 }.status", equalTo("OPEN"));
    }

    /**
     * A BLOCK window on a calendar with no overlapping WINDOW must still
     * generate CLOSED slots over the BLOCK time range, so the UI has data
     * to render even on otherwise-empty days.
     */
    @Test
    @Order(6)
    void blockWindowGeneratesClosedSlotsWithoutOverlappingWindow() {
        // Fresh calendar so we can isolate the BLOCK behaviour.
        String blockOnlyCalendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "block-only-calendar",
                    "name": "Block Only",
                    "autoSlotManager": false
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Holiday",
                    "kind": "BLOCK",
                    "startHour": 13,
                    "endHour": 15,
                    "daysOfWeek": "1,2,3,4,5",
                    "validFrom": "%s"
                }
                """, LocalDate.now()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + blockOnlyCalendarId + "/time-windows")
            .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body(String.format(SLOTS_GENERATE_BODY, blockOnlyCalendarId, A_MONDAY, A_MONDAY))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200)
            .body(SLOTS_CREATED, equalTo(4)); // 13:00, 13:30, 14:00, 14:30

        given()
            .queryParam("calendarId", blockOnlyCalendarId)
            .queryParam("startDate", A_MONDAY.toString())
            .queryParam("endDate",   A_MONDAY.toString())
            .when()
            .get("/api/v1/miot-calendar/slots")
            .then()
            .statusCode(200)
            .body("data.size()", equalTo(4))
            .body("data.status", everyItem(equalTo("CLOSED")))
            .body("data.capacity", everyItem(equalTo(0)));
    }
}
