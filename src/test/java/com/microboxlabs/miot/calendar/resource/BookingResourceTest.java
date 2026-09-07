package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookingResourceTest {

    @TestHTTPResource
    URL url;

    @TestHTTPEndpoint(BookingResource.class)
    @TestHTTPResource
    URL bookingsUrl;

    private static final String RESOURCE_ID = "SRV-001";
    private static final String HISTORICAL_RESOURCE_ID = "ARCHIVE_1584908-V";

    private String calendarId;
    private String bookingId;
    private LocalDate slotDate;
    private LocalDate historicalSlotDate;
    private int slotHour = 10;
    private int slotMinutes = 0;
    private boolean setupDone = false;

    /**
     * One @BeforeEach only: the shared-fixture block below calls {@code given()}, which reads the
     * base URI configured here. JUnit does not order two @BeforeEach methods of the same class, so
     * they cannot be split.
     */
    @BeforeEach
    void setup() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();

        if (setupDone) return;
        setupDone = true;
        slotDate = LocalDate.now().plusDays(1);
        historicalSlotDate = LocalDate.now().minusDays(90);

        // Create a calendar with parallelism=2 (2 resources per slot)
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "booking-test-calendar",
                    "name": "Booking Test Calendar",
                    "parallelism": 2
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Create a time window: 9-17 (480min), capacity=32, parallelism=2
        // → 32/2=16 slots, 480/16=30min each, slot.capacity=2
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Booking Test Window",
                    "startHour": 9,
                    "endHour": 17,
                    "capacity": 32,
                    "daysOfWeek": "1,2,3,4,5,6,7",
                    "validFrom": "%s"
                }
                """, historicalSlotDate))
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

        // Generate and book a slot outside the endpoint's default 30-day
        // window. Resource searches without dates must still find it.
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, calendarId, historicalSlotDate, historicalSlotDate))
            .when()
            .post("/api/v1/miot-calendar/slots/generate")
            .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "%s",
                        "type": "GENERIC",
                        "label": "Historical resource"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": 9,
                        "minutes": 0
                    }
                }
                """, calendarId, HISTORICAL_RESOURCE_ID, historicalSlotDate))
            .when()
            .post(bookingsUrl.toString())
            .then()
            .statusCode(201);
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
                        "id": "%s",
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
                """, calendarId, RESOURCE_ID, slotDate, slotHour, slotMinutes))
            .when()
            .post(bookingsUrl.toString())
            .then()
            .statusCode(201)
            .body("resource.id", equalTo(RESOURCE_ID))
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
            .get(bookingsUrl.toString())
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0))
            .body("data[0].resource.id", equalTo(RESOURCE_ID));
    }

    @Test
    @Order(2)
    void resourceIdSearchWithoutDatesIncludesHistoricalBookings() {
        given()
            .when()
            .get(bookingsUrl.toString())
            .then()
            .statusCode(200)
            .body("data.resource.id", not(hasItem(HISTORICAL_RESOURCE_ID)));

        given()
            .queryParam("resourceIdContains", "archive_1584908")
            .when()
            .get(bookingsUrl.toString())
            .then()
            .statusCode(200)
            .body("total", equalTo(1))
            .body("data[0].resource.id", equalTo(HISTORICAL_RESOURCE_ID))
            .body("data[0].slot.date", equalTo(historicalSlotDate.toString()));
    }

    @Test
    @Order(3)
    void testGetBooking() {
        given()
            .when()
            .get(bookingsUrl + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("id", equalTo(bookingId))
            .body("resource.id", equalTo(RESOURCE_ID));
    }

    @Test
    @Order(4)
    void testGetBookingsByResource() {
        given()
            .when()
            .get(bookingsUrl + "/resource/" + RESOURCE_ID)
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
                        "id": "%s",
                        "type": "SERVICE",
                        "label": "Duplicate booking"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    }
                }
                """, calendarId, RESOURCE_ID, slotDate, slotHour, slotMinutes))
            .when()
            .post(bookingsUrl.toString())
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
            .post(bookingsUrl.toString())
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
            .post(bookingsUrl.toString())
            .then()
            .statusCode(409); // Conflict - slot full
    }

    @Test
    @Order(8)
    void testSlotFullCanBeExplicitlyOverbooked() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-003",
                        "type": "SERVICE",
                        "label": "Overbooked service"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": %d,
                        "minutes": %d
                    },
                    "allowOverbooking": true
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post(bookingsUrl.toString())
            .then()
            .statusCode(201)
            .body("resource.id", equalTo("SRV-003"));

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", slotDate.toString())
            .queryParam("endDate", slotDate.toString())
            .when()
            .get("/api/v1/miot-calendar/slots")
            .then()
            .statusCode(200)
            .body("data.find { it.slotHour == 10 && it.slotMinutes == 0 }.currentOccupancy", equalTo(3))
            .body("data.find { it.slotHour == 10 && it.slotMinutes == 0 }.capacity", equalTo(2))
            // Occupancy stays observable past capacity; availableCapacity never goes negative.
            .body("data.find { it.slotHour == 10 && it.slotMinutes == 0 }.availableCapacity", equalTo(0))
            .body("data.find { it.slotHour == 10 && it.slotMinutes == 0 }.status", equalTo("FULL"));
    }

    @Test
    @Order(9)
    void testOverbookingStillRejectsDuplicateResource() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": { "id": "SRV-003", "type": "SERVICE" },
                    "slot": { "date": "%s", "hour": %d, "minutes": %d },
                    "allowOverbooking": true
                }
                """, calendarId, slotDate, slotHour, slotMinutes))
            .when()
            .post(bookingsUrl.toString())
            .then()
            .statusCode(409);
    }

    @Test
    @Order(7)
    void testUpdateBookingResourceData() {
        // Update the booking's resource payload in place (slot unchanged).
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "resource": {
                        "id": "%s",
                        "type": "SERVICE",
                        "label": "Acme Corp - assigned",
                        "data": {
                            "cliente": "Acme Corp",
                            "assignedCarrier": "carrier-uuid",
                            "assignedDriver": "driver-uuid",
                            "assignedTruck": "truck-uuid"
                        }
                    }
                }
                """, RESOURCE_ID))
            .when()
            .put(bookingsUrl + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("id", equalTo(bookingId))
            .body("resource.id", equalTo(RESOURCE_ID))
            .body("resource.label", equalTo("Acme Corp - assigned"))
            .body("resource.data.assignedCarrier", equalTo("carrier-uuid"));

        // GET reflects the new data.
        given()
            .when()
            .get(bookingsUrl + "/" + bookingId)
            .then()
            .statusCode(200)
            .body("resource.data.assignedTruck", equalTo("truck-uuid"));

        // A PUT that tries to repoint the booking to a different resource is rejected.
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resource": {
                        "id": "SRV-OTHER",
                        "type": "SERVICE"
                    }
                }
                """)
            .when()
            .put(bookingsUrl + "/" + bookingId)
            .then()
            .statusCode(400);
    }

    @Test
    void testUpdateBookingNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "resource": { "id": "SRV-001" }
                }
                """)
            .when()
            .put(bookingsUrl + "/00000000-0000-0000-0000-000000000000")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(10)
    void testCancelBooking() {
        given()
            .when()
            .delete(bookingsUrl + "/" + bookingId)
            .then()
            .statusCode(204);

        // Verify booking is gone
        given()
            .when()
            .get(bookingsUrl + "/" + bookingId)
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
            .post(bookingsUrl.toString())
            .then()
            .statusCode(400);
    }

    @Test
    void testBookingNotFound() {
        given()
            .when()
            .get(bookingsUrl + "/00000000-0000-0000-0000-000000000000")
            .then()
            .statusCode(404);
    }

    /**
     * A booking against a slot covered by a BLOCK time window must be rejected
     * with the SLOT_CLOSED error (BLOCK windows generate CLOSED slots, which
     * BookingValidationService already refuses).
     */
    @Test
    @Order(11)
    void testBookingRejectedOnBlockSlot() {
        // Fresh calendar so the BLOCK doesn't disturb sibling tests.
        String blockedCalendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "blocked-calendar",
                    "name": "Blocked Calendar",
                    "parallelism": 1
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        LocalDate blockDate = LocalDate.now().plusDays(2);
        int dayOfWeek = blockDate.getDayOfWeek().getValue();

        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Blocked Period",
                    "kind": "BLOCK",
                    "startHour": 10,
                    "endHour": 12,
                    "daysOfWeek": "%d",
                    "validFrom": "%s"
                }
                """, dayOfWeek, LocalDate.now()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + blockedCalendarId + "/time-windows")
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
                """, blockedCalendarId, blockDate, blockDate))
            .when()
            .post("/api/v1/miot-calendar/slots/generate")
            .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .header("X-User-Id", "test-user")
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": {
                        "id": "SRV-BLOCKED",
                        "type": "SERVICE",
                        "label": "Should fail"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": 10,
                        "minutes": 0
                    }
                }
                """, blockedCalendarId, blockDate))
            .when()
            .post(bookingsUrl.toString())
            .then()
            .statusCode(409);
    }
}
