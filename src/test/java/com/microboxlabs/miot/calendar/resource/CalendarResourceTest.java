package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
@TestSecurity(user = "test-user", roles = {})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarResourceTest {

    @TestHTTPResource
    URL url;

    private String calendarId;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    @Test
    @Order(1)
    void testCreateCalendar() {
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "test-calendar",
                    "name": "Test Calendar",
                    "description": "Calendar for testing",
                    "timezone": "America/Santiago"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("code", equalTo("test-calendar"))
            .body("name", equalTo("Test Calendar"))
            .body("active", equalTo(true))
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void testListCalendars() {
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    @Order(3)
    void testGetCalendar() {
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("code", equalTo("test-calendar"));
    }

    @Test
    @Order(4)
    void testUpdateCalendar() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Updated Test Calendar"
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("name", equalTo("Updated Test Calendar"));
    }

    @Test
    @Order(5)
    void testCreateTimeWindow() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Morning Shift",
                    "startHour": 8,
                    "endHour": 12,
                    "slotDurationMinutes": 30,
                    "capacityPerSlot": 2,
                    "daysOfWeek": "MON,TUE,WED,THU,FRI",
                    "validFrom": "2025-01-01"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201)
            .body("name", equalTo("Morning Shift"))
            .body("startHour", equalTo(8))
            .body("endHour", equalTo(12));
    }

    @Test
    @Order(6)
    void testListTimeWindows() {
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    @Order(7)
    void testDeactivateCalendar() {
        given()
            .when()
            .delete("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(204);

        // Verify it's deactivated
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    @Test
    void testCreateCalendarValidation() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Missing Code"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(400);
    }

    @Test
    void testGetNonExistentCalendar() {
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/00000000-0000-0000-0000-000000000000")
            .then()
            .statusCode(404);
    }
}
