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
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Booking lifecycle status (CALSYNC C1): default on create, forward-only
 * transitions on PUT, 409 on regression, and the documented re-plan reset
 * (move to a different slot → back to PLANNED).
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingStatusLifecycleTest {

    private static final String BOOKINGS = "/api/v1/miot-calendar/bookings";

    @TestHTTPResource
    URL url;

    private String calendarId;
    private String bookingId;
    private LocalDate slotDate;
    private final int slotHour = 10;
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

        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "status-lifecycle-calendar",
                    "name": "Status Lifecycle Calendar",
                    "parallelism": 2
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
                    "name": "Status Lifecycle Window",
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

    private String bookingBody(String resourceId, int hour, int minutes, String statusLine) {
        return String.format("""
            {
                "calendarId": "%s",
                "resource": {
                    "id": "%s",
                    "type": "SERVICE",
                    "data": { "mintral_serviceCode": "%s" }
                },
                "slot": { "date": "%s", "hour": %d, "minutes": %d }%s
            }
            """, calendarId, resourceId, resourceId, slotDate, hour, minutes,
            statusLine == null ? "" : ",\n\"status\": \"" + statusLine + "\"");
    }

    @Test
    @Order(1)
    void createDefaultsToPlannedAndExposesAuditFields() {
        bookingId = given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "calsync-test")
            .body(bookingBody("STS-001", slotHour, 0, null))
            .when()
            .post(BOOKINGS)
            .then()
            .statusCode(201)
            .body("status", equalTo("PLANNED"))
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue())
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void createAcceptsExplicitStatus() {
        given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "calsync-test")
            .body(bookingBody("STS-002", slotHour, 30, "ASSIGNED"))
            .when()
            .post(BOOKINGS)
            .then()
            .statusCode(201)
            .body("status", equalTo("ASSIGNED"));
    }

    @Test
    @Order(3)
    void createRejectsUnknownStatus() {
        given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "calsync-test")
            .body(bookingBody("STS-003", 11, 0, "DONE"))
            .when()
            .post(BOOKINGS)
            .then()
            .statusCode(400);
    }

    @Test
    @Order(4)
    void statusOnlyPutMovesForward() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "IN_TRANSIT" }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_TRANSIT"))
            .body("resource.id", equalTo("STS-001"));
    }

    @Test
    @Order(5)
    void sameStatusPutIsANoOp() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "IN_TRANSIT" }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("status", equalTo("IN_TRANSIT"));
    }

    @Test
    @Order(6)
    void regressionIsRejectedWith409() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "ASSIGNED" }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(409);
    }

    @Test
    @Order(7)
    void emptyBodyIsRejectedWith400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(400);
    }

    @Test
    @Order(8)
    void statusAndResourceUpdateTogether() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resource": {
                        "id": "STS-001",
                        "type": "SERVICE",
                        "label": "updated label",
                        "data": { "mintral_serviceCode": "STS-001", "assignedDriver": "Jane" }
                    },
                    "status": "ARRIVED"
                }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("status", equalTo("ARRIVED"))
            .body("resource.label", equalTo("updated label"))
            .body("resource.data.assignedDriver", equalTo("Jane"));
    }

    @Test
    @Order(9)
    void moveToDifferentSlotResetsStatusToPlanned() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                { "slot": { "date": "%s", "hour": 12, "minutes": 0 } }
                """, slotDate))
            .when()
            .post(BOOKINGS + "/" + bookingId + "/move")
            .then()
            .statusCode(200)
            .body("status", equalTo("PLANNED"))
            .body("slot.hour", equalTo(12));
    }

    @Test
    @Order(10)
    void cancelledIsReachableFromAnywhereAndTerminal() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "CANCELLED" }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("status", equalTo("CANCELLED"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "status": "FINISHED" }
                """)
            .when()
            .put(BOOKINGS + "/" + bookingId)
            .then()
            .statusCode(409);
    }
}
