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
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotResourceTest {

    @TestHTTPResource
    URL url;

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
            .post("/api/calendars")
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
                    "daysOfWeek": "MON,TUE,WED,THU,FRI,SAT,SUN",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);
    }

    @Test
    @Order(0)
    void testGenerateSlots() {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);

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
            .post("/api/slots/generate")
            .then()
            .statusCode(200)
            .body("slotsCreated", greaterThan(0))
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
            .get("/api/slots")
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
            .get("/api/slots/" + slotId)
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
            .get("/api/slots")
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
            .patch("/api/slots/" + slotId + "/status")
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
            .patch("/api/slots/" + slotId + "/status")
            .then()
            .statusCode(200)
            .body("status", equalTo("OPEN"));
    }

    @Test
    void testListSlotsRequiresCalendarId() {
        given()
            .when()
            .get("/api/slots")
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
            .post("/api/slots/generate")
            .then()
            .statusCode(400);
    }
}
