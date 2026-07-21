package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;

/**
 * PATCH /bookings/resource/{resourceId} (CALSYNC C2): shallow-merge patch +
 * status by external resource id, calendar scoping, idempotency, and the
 * status filter on the bookings list.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingResourcePatchTest {

    private static final String BOOKINGS = "/api/v1/miot-calendar/bookings";
    private static final String RESOURCE_ID = "PATCH-001";

    @TestHTTPResource
    URL url;

    private String calendarA;
    private String calendarB;
    private LocalDate slotDate;
    private boolean setupDone = false;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    private String createCalendar(String code) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "code": "%s",
                    "name": "%s",
                    "parallelism": 2
                }
                """, code, code))
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    }

    private void addWindowAndSlots(String calendarId) {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Patch Test Window",
                    "startHour": 9,
                    "endHour": 17,
                    "capacity": 32,
                    "daysOfWeek": "1,2,3,4,5,6,7",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, calendarId, slotDate, slotDate.plusDays(7)))
            .when()
            .post("/api/v1/miot-calendar/slots/generate")
            .then()
            .statusCode(200);
    }

    private void createBooking(String calendarId, String resourceId, int hour) {
        given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "calsync-test")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "%s",
                        "type": "SERVICE",
                        "data": { "origen": "ANF", "destino": "SPC", "prioridad": 1 }
                    },
                    "slot": { "date": "%s", "hour": %d, "minutes": 0 }
                }
                """, calendarId, resourceId, slotDate, hour))
            .when()
            .post(BOOKINGS)
            .then()
            .statusCode(201);
    }

    @BeforeEach
    void setup() {
        if (setupDone) return;
        setupDone = true;
        slotDate = LocalDate.now().plusDays(1);

        calendarA = createCalendar("patch-cal-a");
        calendarB = createCalendar("patch-cal-b");
        addWindowAndSlots(calendarA);
        addWindowAndSlots(calendarB);

        // The same resource booked in two calendars — scoping must matter.
        createBooking(calendarA, RESOURCE_ID, 10);
        createBooking(calendarB, RESOURCE_ID, 11);
    }

    @Test
    @Order(1)
    void patchStatusOnlyScopedToOneCalendar() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "ASSIGNED" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data", hasSize(1))
            .body("data[0].status", equalTo("ASSIGNED"))
            .body("data[0].calendarId", equalTo(calendarA));

        // The unscoped sibling in calendar B is untouched.
        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarB + "&startDate=" + slotDate + "&endDate=" + slotDate)
            .then()
            .statusCode(200)
            .body("data[0].status", equalTo("PLANNED"));
    }

    @Test
    @Order(2)
    void patchDataShallowMergePreservesExistingKeys() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "resourceData": { "assignedDriver": "Jane Doe", "prioridad": 5 } }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].resource.data.assignedDriver", equalTo("Jane Doe"))
            .body("data[0].resource.data.prioridad", equalTo(5))
            // Keys absent from the patch survive the merge.
            .body("data[0].resource.data.origen", equalTo("ANF"))
            .body("data[0].resource.data.destino", equalTo("SPC"));
    }

    @Test
    @Order(3)
    void patchStatusAndDataTogether() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resourceData": { "eta": "2026-07-16T05:00:00Z" },
                    "status": "IN_TRANSIT"
                }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].status", equalTo("IN_TRANSIT"))
            .body("data[0].resource.data.eta", equalTo("2026-07-16T05:00:00Z"))
            .body("data[0].resource.data.assignedDriver", equalTo("Jane Doe"));
    }

    @Test
    @Order(4)
    void patchIsIdempotent() {
        for (int i = 0; i < 2; i++) {
            given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "resourceData": { "eta": "2026-07-16T05:00:00Z" },
                        "status": "IN_TRANSIT"
                    }
                    """)
                .when()
                .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
                .then()
                .statusCode(200)
                .body("data[0].status", equalTo("IN_TRANSIT"));
        }
    }

    @Test
    @Order(5)
    void unscopedPatchHitsEveryCalendar() {
        // No calendarId: both bookings move forward (B: PLANNED -> IN_TRANSIT
        // is a legal forward jump; A: same-status no-op).
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "IN_TRANSIT" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID)
            .then()
            .statusCode(200)
            .body("data", hasSize(2));
    }

    @Test
    @Order(6)
    void regressionOnAnyMatchIs409() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "ASSIGNED" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(409);
    }

    @Test
    @Order(7)
    void emptyPatchIs400AndUnknownStatusIs400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID)
            .then()
            .statusCode(400);

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "DONE" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID)
            .then()
            .statusCode(400);
    }

    @Test
    @Order(8)
    void unknownResourceIs404() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "FINISHED" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/NO-SUCH-RESOURCE")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(9)
    void listFilterByStatusCombinesWithCalendarAndRange() {
        // Move A's booking to FINISHED so the two calendars diverge.
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "FINISHED" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200);

        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarA + "&startDate=" + slotDate
                + "&endDate=" + slotDate + "&status=FINISHED")
            .then()
            .statusCode(200)
            .body("data", hasSize(1))
            .body("data[0].status", equalTo("FINISHED"));

        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarA + "&startDate=" + slotDate
                + "&endDate=" + slotDate + "&status=PLANNED")
            .then()
            .statusCode(200)
            .body("data", hasSize(0));

        // Unknown status value on the filter is a 400, not a silent empty list.
        given()
            .when()
            .get(BOOKINGS + "?status=DONE")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(10)
    void syncStatusPatchStampsDetailAndTimestampWithoutTouchingLifecycle() {
        // A syncStatus-only patch is a valid body (no status/resourceData needed).
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "syncStatus": "PENDING" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].syncStatus", equalTo("PENDING"))
            .body("data[0].syncAt", org.hamcrest.CoreMatchers.notNullValue())
            // The lifecycle status is untouched (FINISHED from the previous test).
            .body("data[0].status", equalTo("FINISHED"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "syncStatus": "CONFIRMED", "syncDetail": "Alerce accepted (code=OK)" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].syncStatus", equalTo("CONFIRMED"))
            .body("data[0].syncDetail", equalTo("Alerce accepted (code=OK)"));
    }

    @Test
    @Order(11)
    void syncStatusHasNoForwardOnlyRule() {
        // A re-assignment legitimately reopens the confirmation window:
        // CONFIRMED -> PENDING must NOT be rejected as a regression.
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "syncStatus": "PENDING" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].syncStatus", equalTo("PENDING"))
            // The previous detail does not survive the transition.
            .body("data[0].syncDetail", nullValue());
    }

    @Test
    @Order(12)
    void unknownSyncStatusIs400AndUntouchedBookingsReadNull() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "syncStatus": "MAYBE" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID)
            .then()
            .statusCode(400);

        // The calendar-B sibling has never been sync-patched: null = untracked.
        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarB + "&startDate=" + slotDate + "&endDate=" + slotDate)
            .then()
            .statusCode(200)
            .body("data[0].syncStatus", nullValue());
    }
}
