package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
@TestSecurity(user = "test-user", roles = {})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingResourceTest {

    @TestHTTPResource
    URL url;

    private String calendarId;
    private String bookingId;
    private LocalDate slotDate;
    private int slotHour = 10;
    private int slotMinutes = 0;
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

        // Create a calendar
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "booking-test-calendar",
                    "name": "Booking Test Calendar"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Create a time window
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Booking Test Window",
                    "startHour": 9,
                    "endHour": 17,
                    "slotDurationMinutes": 30,
                    "capacityPerSlot": 2,
                    "daysOfWeek": "MON,TUE,WED,THU,FRI,SAT,SUN",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);

        // Generate slots
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

    @Test
    @Order(1)
    void testCreateBooking() {
        bookingId = given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "test-user")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-001",
                        "type": "SERVICE",
                        "label": "Acme Corp - Santiago to Valparaiso",
                        "data": {
                            "cliente": "Acme Corp",
                            "origen": "Santiago",
                            "destino": "Valparaiso",
                            "tipoViaje": "Sider"
                        }
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(201)
            .body("resource.id", equalTo("SRV-001"))
            .body("resource.type", equalTo("SERVICE"))
            .body("createdBy", equalTo("test-user"))
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void testListBookings() {
        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", slotDate.toString())
            .queryParam("endDate", slotDate.plusDays(7).toString())
            .when()
            .get("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0))
            .body("data[0].resource.id", equalTo("SRV-001"));
    }

    @Test
    @Order(3)
    void testGetBooking() {
        given()
            .when()
            .get("/api/v1/miot-calendar/bookings/" + bookingId)
            .then()
            .statusCode(200)
            .body("id", equalTo(bookingId))
            .body("resource.id", equalTo("SRV-001"));
    }

    @Test
    @Order(4)
    void testGetBookingsByResource() {
        given()
            .when()
            .get("/api/v1/miot-calendar/bookings/resource/SRV-001")
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0));
    }

    @Test
    @Order(5)
    void testDuplicateBookingRejected() {
        // Try to book the same resource in the same slot
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-001",
                        "type": "SERVICE",
                        "label": "Duplicate booking"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(409); // Conflict
    }

    @Test
    @Order(6)
    void testCreateSecondBookingInSameSlot() {
        // Book a different resource in the same slot (should succeed - capacity is 2)
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-002",
                        "type": "SERVICE",
                        "label": "Another service"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(201);
    }

    @Test
    @Order(7)
    void testSlotFullRejected() {
        // Try to book a third resource (capacity is 2, so should fail)
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-003",
                        "type": "SERVICE",
                        "label": "Third service"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(409); // Conflict - slot full
    }

    @Test
    @Order(8)
    void testCancelBooking() {
        given()
            .when()
            .delete("/api/v1/miot-calendar/bookings/" + bookingId)
            .then()
            .statusCode(204);

        // Verify booking is gone
        given()
            .when()
            .get("/api/v1/miot-calendar/bookings/" + bookingId)
            .then()
            .statusCode(404);
    }

    @Test
    void testCreateBookingValidation() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resource": {
                        "id": "test"
                    }
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/bookings")
            .then()
            .statusCode(400);
    }

    @Test
    void testBookingNotFound() {
        given()
            .when()
            .get("/api/v1/miot-calendar/bookings/00000000-0000-0000-0000-000000000000")
            .then()
            .statusCode(404);
    }
}
