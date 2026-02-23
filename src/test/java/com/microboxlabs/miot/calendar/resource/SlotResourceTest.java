package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPEndpoint;
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
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotResourceTest {

    @TestHTTPResource
    URL url;

    @TestHTTPEndpoint(SlotResource.class)
    @TestHTTPResource
    URL slotsUrl;

    @TestHTTPEndpoint(SlotResource.class)
    @TestHTTPResource("generate")
    URL slotsGenerateUrl;

    private String calendarId;
    private String slotId;
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

        // Create a calendar for testing
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "slot-test-calendar",
                    "name": "Slot Test Calendar"
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
                    "name": "Test Window",
                    "startHour": 9,
                    "endHour": 17,
                    "slotDurationMinutes": 30,
                    "capacityPerSlot": 3,
                    "daysOfWeek": "1,2,3,4,5,6,7",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);
    }

    @Test
    @Order(0)
    void testGenerateSlots() {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);

        // With auto-generation now active, slots are created immediately when the
        // time window is persisted. A subsequent explicit generate call for the same
        // range must report the existing slots as skipped rather than re-creating them.
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, calendarId, today, nextWeek))
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(200)
            .body("slotsCreated", greaterThanOrEqualTo(0))
            .body("slotsSkipped", greaterThan(0))
            .body("message", containsString("Generated"));
    }

    @Test
    @Order(2)
    void testListSlots() {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);

        slotId = given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", today.toString())
            .queryParam("endDate", nextWeek.toString())
            .when()
            .get(slotsUrl.toString())
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0))
            .body("data[0].status", equalTo("OPEN"))
            .extract()
            .path("data[0].id");
    }

    @Test
    @Order(3)
    void testGetSlot() {
        given()
            .when()
            .get(slotsUrl + "/" + slotId)
            .then()
            .statusCode(200)
            .body("id", equalTo(slotId));
    }

    @Test
    @Order(4)
    void testListAvailableSlots() {
        LocalDate today = LocalDate.now();

        given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", today.toString())
            .queryParam("endDate", today.plusDays(7).toString())
            .queryParam("available", true)
            .when()
            .get(slotsUrl.toString())
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0));
    }

    @Test
    @Order(5)
    void testUpdateSlotStatus() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "status": "CLOSED"
                }
                """)
            .when()
            .patch(slotsUrl + "/" + slotId + "/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("CLOSED"));

        // Reopen the slot
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "status": "OPEN"
                }
                """)
            .when()
            .patch(slotsUrl + "/" + slotId + "/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("OPEN"));
    }

    @Test
    void testListSlotsRequiresCalendarId() {
        given()
            .when()
            .get(slotsUrl.toString())
            .then()
            .statusCode(400);
    }

    @Test
    void testGenerateSlotsValidation() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "startDate": "2025-01-01"
                }
                """)
            .when()
            .post(slotsGenerateUrl.toString())
            .then()
            .statusCode(400);
    }
}
