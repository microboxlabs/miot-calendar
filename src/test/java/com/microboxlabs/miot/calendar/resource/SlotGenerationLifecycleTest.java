package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Integration tests for the slot-generation lifecycle introduced to fix
 * https://github.com/microboxlabs/modulariot/issues/53.
 *
 * <p>Two gaps existed before the fix:
 * <ol>
 *   <li>A SlotManager provisioned via {@code autoSlotManager: true} never ran
 *       immediately; slots only appeared after the hourly cron or a manual trigger.</li>
 *   <li>Creating or updating a time window never triggered the SlotManager, so
 *       changes were not immediately reflected in available slots.</li>
 * </ol>
 *
 * <p>Each test uses a unique calendar code so tests remain independent even when
 * the full suite shares the same Quarkus instance.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotGenerationLifecycleTest {

    /** Inclusive start for slot queries — far enough in the past to cover validFrom. */
    private static final LocalDate QUERY_FROM = LocalDate.now();
    /** Exclusive end: default daysInAdvance is 30. */
    private static final LocalDate QUERY_TO   = LocalDate.now().plusDays(30);

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createCalendar(String code, boolean autoSlotManager) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "code": "%s",
                    "name": "%s",
                    "timezone": "America/Santiago",
                    "autoSlotManager": %b
                }
                """, code, code, autoSlotManager))
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private String createTimeWindow(String calendarId, String daysOfWeek) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Lifecycle Test Window",
                    "startHour": 9,
                    "endHour": 11,
                    "slotDurationMinutes": 60,
                    "capacityPerSlot": 1,
                    "daysOfWeek": "%s",
                    "validFrom": "%s"
                }
                """, daysOfWeek, QUERY_FROM))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private String createSlotManager(String calendarId) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "daysInAdvance": 30,
                    "batchDays": 7
                }
                """, calendarId))
            .when()
            .post("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(201)
            .extract().path("id");
    }

    private int slotCount(String calendarId) {
        return given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", QUERY_FROM.toString())
            .queryParam("endDate",   QUERY_TO.toString())
            .when()
            .get("/api/v1/miot-calendar/slots")
            .then()
            .statusCode(200)
            .extract().path("data.size()");
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    /**
     * Full new-calendar setup flow: create calendar (autoSlotManager=true), then
     * add a time window. Slots must exist immediately after the time-window POST
     * without any manual trigger call.
     *
     * <p>Before the fix: the manager ran at calendar-creation time (0 slots — no
     * time windows yet), advanced {@code generatedThrough}, and the subsequent
     * time-window trigger was SKIPPED.  After the fix: the time-window trigger
     * sets a reprocess range so the manager regenerates the full window.
     */
    @Test
    void testSlotsExistImmediatelyAfterFullCalendarSetup() {
        String calendarId = createCalendar("lc-full-setup", true);
        createTimeWindow(calendarId, "1,2,3,4,5");   // weekdays, numeric codes

        Assertions.assertTrue(slotCount(calendarId) > 0,
            "Slots must be generated immediately after time-window creation");
    }

    /**
     * When autoSlotManager=true but NO time window has been created yet, the
     * calendar-creation trigger runs the manager immediately and gracefully
     * returns zero slots (no time windows → nothing to generate).
     */
    @Test
    void testCalendarCreationWithNoTimeWindowsProducesZeroSlots() {
        String calendarId = createCalendar("lc-no-tw", true);

        Assertions.assertEquals(0, slotCount(calendarId),
            "No slots expected when calendar has no time windows");
    }

    /**
     * Time-window creation must trigger the SlotManager even when the calendar
     * was created with autoSlotManager=false and the manager was added afterwards
     * via the REST API (which does not auto-trigger a run).
     *
     * <p>Sequence:
     * <ol>
     *   <li>Create calendar (autoSlotManager=false) — no manager, no trigger.</li>
     *   <li>Create slot manager via API — persisted but never run.</li>
     *   <li>Verify 0 slots — manager exists but hasn't executed.</li>
     *   <li>POST time window — trigger fires → manager runs → slots generated.</li>
     *   <li>Verify slots > 0.</li>
     * </ol>
     */
    @Test
    void testTimeWindowCreationTriggersSlotGeneration() {
        String calendarId = createCalendar("lc-tw-create", false);
        createSlotManager(calendarId);

        Assertions.assertEquals(0, slotCount(calendarId),
            "No slots expected before any time window is created");

        createTimeWindow(calendarId, "1,2,3,4,5");

        Assertions.assertTrue(slotCount(calendarId) > 0,
            "Slots must be generated immediately after time-window POST");
    }

    /**
     * Updating a time window must trigger the SlotManager to re-run.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Create calendar (autoSlotManager=false).</li>
     *   <li>POST time window — no manager yet → no trigger → 0 slots.</li>
     *   <li>Create slot manager via API — persisted but not run.</li>
     *   <li>Verify 0 slots.</li>
     *   <li>PUT time window (extend to all days) — trigger fires → manager runs.</li>
     *   <li>Verify slots > 0.</li>
     * </ol>
     */
    @Test
    void testTimeWindowUpdateTriggersSlotGeneration() {
        String calendarId = createCalendar("lc-tw-update", false);
        // No manager yet → createTimeWindow trigger guard returns early
        String timeWindowId = createTimeWindow(calendarId, "1,2,3,4,5");

        Assertions.assertEquals(0, slotCount(calendarId),
            "No slots expected before slot manager is created");

        createSlotManager(calendarId);

        Assertions.assertEquals(0, slotCount(calendarId),
            "No slots expected after manager created but not yet triggered");

        // Update the time window — this must trigger the manager
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "daysOfWeek": "1,2,3,4,5,6,7"
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows/" + timeWindowId)
            .then()
            .statusCode(200);

        Assertions.assertTrue(slotCount(calendarId) > 0,
            "Slots must be generated immediately after time-window PUT");
    }

    /**
     * When no daysOfWeek is supplied in the request, the API must store the
     * ISO-numeric default "1,2,3,4,5" (Mon–Fri) so it matches the numeric codes
     * produced by the slot generator.
     *
     * <p>Before the fix the default was the name-format string "MON,TUE,WED,THU,FRI"
     * which never matched the generator's numeric day codes, causing every
     * time window created without an explicit daysOfWeek to silently produce 0 slots.
     */
    @Test
    void testDefaultDaysOfWeekIsStoredAsNumericCodes() {
        String calendarId = createCalendar("lc-default-dow", false);

        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Default Days Window",
                    "startHour": 9,
                    "endHour": 10,
                    "slotDurationMinutes": 60,
                    "validFrom": "%s"
                }
                """, QUERY_FROM))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);

        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(200)
            .body("[0].daysOfWeek", equalTo("1,2,3,4,5"));
    }
}
