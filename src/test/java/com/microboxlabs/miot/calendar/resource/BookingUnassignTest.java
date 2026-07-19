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
 * POST /bookings/resource/{resourceId}/unassign: the sanctioned ASSIGNED →
 * PLANNED regression for the coordinator's {@code presentDriver →
 * assignDriver} revert.
 *
 * <p>The scenario this exists for: a service is assigned from the calendar,
 * then the workflow is reverted. A plain status patch to PLANNED is rejected
 * as a regression (409) and — because the executor treats 409 as benign —
 * silently reports success while the card keeps showing the old driver.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingUnassignTest {

    private static final String BOOKINGS = "/api/v1/miot-calendar/bookings";
    private static final String RESOURCE_ID = "UNASSIGN-001";

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

    @BeforeEach
    void setup() {
        if (setupDone) return;
        setupDone = true;
        slotDate = LocalDate.now().plusDays(1);

        calendarA = createCalendar("unassign-cal-a");
        calendarB = createCalendar("unassign-cal-b");
        addWindowAndSlots(calendarA);
        addWindowAndSlots(calendarB);

        // The same resource booked in two calendars — scoping must matter.
        createBooking(calendarA, RESOURCE_ID, 10);
        createBooking(calendarB, RESOURCE_ID, 11);
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
                    "name": "Unassign Test Window",
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
                        "data": { "origen": "ANF", "destino": "SPC" }
                    },
                    "slot": { "date": "%s", "hour": %d, "minutes": 0 }
                }
                """, calendarId, resourceId, slotDate, hour))
            .when()
            .post(BOOKINGS)
            .then()
            .statusCode(201);
    }

    /** Drives a booking to ASSIGNED with a full driver tuple, as the coordinator does. */
    private void assignInCalendar(String calendarId) {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resourceData": {
                        "assignedCarrier": "TRANSPORTES X",
                        "assignedDriver": "Jane Doe",
                        "assignedTruck": "AABB11"
                    },
                    "status": "ASSIGNED"
                }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarId)
            .then()
            .statusCode(200)
            .body("data[0].status", equalTo("ASSIGNED"));
    }

    private static String unassignBody() {
        return """
            { "clearDataKeys": ["assignedCarrier", "assignedDriver", "assignedTruck"] }
            """;
    }

    /**
     * The reported defect, end to end: assigned from the calendar, then
     * reverted. Status goes back to PLANNED and the driver tuple is gone —
     * while the route keys, which nobody asked to clear, survive.
     */
    @Test
    @Order(1)
    void unassignResetsStatusAndClearsTheAssignmentTuple() {
        assignInCalendar(calendarA);

        given()
            .contentType(ContentType.JSON)
            .body(unassignBody())
            .when()
            .post(BOOKINGS + "/resource/" + RESOURCE_ID + "/unassign?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data", hasSize(1))
            .body("data[0].status", equalTo("PLANNED"))
            .body("data[0].resource.data.assignedCarrier", nullValue())
            .body("data[0].resource.data.assignedDriver", nullValue())
            .body("data[0].resource.data.assignedTruck", nullValue())
            // Untargeted keys are preserved — this clears a tuple, not a payload.
            .body("data[0].resource.data.origen", equalTo("ANF"))
            .body("data[0].resource.data.destino", equalTo("SPC"));
    }

    /** The slot is kept: an unassign is not an unplan. */
    @Test
    @Order(2)
    void unassignKeepsTheSlot() {
        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarA + "&startDate=" + slotDate + "&endDate=" + slotDate)
            .then()
            .statusCode(200)
            .body("data[0].slot.date", equalTo(slotDate.toString()))
            .body("data[0].slot.hour", equalTo(10));
    }

    /**
     * A forward path arrives here too ({@code planService → assignDriver}), so
     * unassigning an already-PLANNED booking must be a clean no-op rather than
     * a 409 the caller has to special-case.
     */
    @Test
    @Order(3)
    void unassignIsIdempotent() {
        for (int i = 0; i < 2; i++) {
            given()
                .contentType(ContentType.JSON)
                .body(unassignBody())
                .when()
                .post(BOOKINGS + "/resource/" + RESOURCE_ID + "/unassign?calendarId=" + calendarA)
                .then()
                .statusCode(200)
                .body("data[0].status", equalTo("PLANNED"));
        }
    }

    /** Scoping matters: the sibling booking in calendar B is untouched. */
    @Test
    @Order(4)
    void unassignIsScopedToOneCalendar() {
        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarB + "&startDate=" + slotDate + "&endDate=" + slotDate)
            .then()
            .statusCode(200)
            .body("data[0].status", equalTo("PLANNED"))
            .body("data[0].resource.data.assignedDriver", nullValue());
    }

    /** No body at all is "reset the lifecycle, touch no payload". */
    @Test
    @Order(5)
    void unassignWithoutBodyResetsStatusAndLeavesDataAlone() {
        assignInCalendar(calendarA);

        given()
            .contentType(ContentType.JSON)
            .when()
            .post(BOOKINGS + "/resource/" + RESOURCE_ID + "/unassign?calendarId=" + calendarA)
            .then()
            .statusCode(200)
            .body("data[0].status", equalTo("PLANNED"))
            // Not named for clearing, so it stays — the caller owns the vocabulary.
            .body("data[0].resource.data.assignedDriver", equalTo("Jane Doe"));
    }

    /**
     * Past ASSIGNED the truck is already moving, so an unassign means the
     * booking and the workflow have genuinely diverged. That is a real 409,
     * not something to paper over.
     */
    @Test
    @Order(6)
    void unassignFromInTransitIsRejected() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "IN_TRANSIT" }
                """)
            .when()
            .patch(BOOKINGS + "/resource/" + RESOURCE_ID + "?calendarId=" + calendarA)
            .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body(unassignBody())
            .when()
            .post(BOOKINGS + "/resource/" + RESOURCE_ID + "/unassign?calendarId=" + calendarA)
            .then()
            .statusCode(409);

        // Rejected all-or-nothing: the booking kept its status.
        given()
            .when()
            .get(BOOKINGS + "?calendarId=" + calendarA + "&startDate=" + slotDate + "&endDate=" + slotDate)
            .then()
            .body("data[0].status", equalTo("IN_TRANSIT"));
    }

    @Test
    @Order(7)
    void unassignUnknownResourceIs404() {
        given()
            .contentType(ContentType.JSON)
            .body(unassignBody())
            .when()
            .post(BOOKINGS + "/resource/NO-SUCH-RESOURCE/unassign")
            .then()
            .statusCode(404);
    }
}
