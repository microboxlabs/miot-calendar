package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotManagerResourceTest {

    @TestHTTPResource
    URL url;

    private String calendarId;
    private String managerId;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    // ── Setup: create a calendar with a time window ─────────────────────

    @Test
    @Order(0)
    void setupCalendar() {
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "mgr-test-calendar",
                    "name": "Manager Test Calendar",
                    "timezone": "America/Santiago"
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
                    "name": "All Day Window",
                    "startHour": 8,
                    "endHour": 12,
                    "slotDurationMinutes": 60,
                    "capacityPerSlot": 2,
                    "daysOfWeek": "MON,TUE,WED,THU,FRI,SAT,SUN",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/v1/miot-calendar/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);
    }

    // ── Manager CRUD ─────────────────────────────────────────────────────

    @Test
    @Order(1)
    void testCreateManager() {
        managerId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "active": true,
                    "daysInAdvance": 14,
                    "batchDays": 7
                }
                """, calendarId))
            .when()
            .post("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(201)
            .body("calendarId", equalTo(calendarId))
            .body("active", equalTo(true))
            .body("daysInAdvance", equalTo(14))
            .body("batchDays", equalTo(7))
            .body("lastRunStatus", nullValue())
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void testListManagers() {
        given()
            .when()
            .get("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(3)
    void testGetManager() {
        given()
            .when()
            .get("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(200)
            .body("id", equalTo(managerId))
            .body("daysInAdvance", equalTo(14));
    }

    @Test
    @Order(4)
    void testUpdateManager() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "daysInAdvance": 21,
                    "batchDays": 3
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(200)
            .body("daysInAdvance", equalTo(21))
            .body("batchDays", equalTo(3));
    }

    // ── Trigger run ───────────────────────────────────────────────────────

    @Test
    @Order(5)
    void testRunOneManager() {
        given()
            .when()
            .post("/api/v1/miot-calendar/slot-managers/" + managerId + "/run")
            .then()
            .statusCode(200)
            .body("managerId", equalTo(managerId))
            .body("triggeredBy", equalTo("API"))
            .body("status", equalTo("SUCCESS"))
            .body("slotsCreated", greaterThan(0))
            .body("generatedFrom", notNullValue())
            .body("generatedThrough", notNullValue());
    }

    @Test
    @Order(6)
    void testRunOneManagerAgainIsSkipped() {
        // Running again immediately: already generated through the horizon
        given()
            .when()
            .post("/api/v1/miot-calendar/slot-managers/" + managerId + "/run")
            .then()
            .statusCode(200)
            .body("status", equalTo("SKIPPED"));
    }

    @Test
    @Order(7)
    void testRunAll() {
        given()
            .when()
            .post("/api/v1/miot-calendar/slot-managers/run")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(0)); // may be empty if soft-lock triggers
    }

    // ── Reprocess ─────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void testReprocess() {
        LocalDate from = LocalDate.now();
        LocalDate to   = LocalDate.now().plusDays(2);

        // Schedule reprocess window
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "reprocessFrom": "%s",
                    "reprocessTo":   "%s"
                }
                """, from, to))
            .when()
            .put("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(200)
            .body("reprocessFrom", equalTo(from.toString()))
            .body("reprocessTo",   equalTo(to.toString()));

        // Trigger run — should use reprocess window and clear it on success
        given()
            .when()
            .post("/api/v1/miot-calendar/slot-managers/" + managerId + "/run")
            .then()
            .statusCode(200)
            .body("status", equalTo("SUCCESS"))
            .body("generatedFrom",    equalTo(from.toString()))
            .body("generatedThrough", equalTo(to.toString()));

        // Reprocess fields should be cleared after successful run
        given()
            .when()
            .get("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(200)
            .body("reprocessFrom", nullValue())
            .body("reprocessTo",   nullValue())
            .body("lastRunStatus", equalTo("SUCCESS"));
    }

    // ── Run history ───────────────────────────────────────────────────────

    @Test
    @Order(9)
    void testGetRunsByManager() {
        given()
            .queryParam("limit", 10)
            .when()
            .get("/api/v1/miot-calendar/slot-managers/" + managerId + "/runs")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].managerId", equalTo(managerId));
    }

    @Test
    @Order(10)
    void testGetAllRuns() {
        given()
            .queryParam("limit", 50)
            .when()
            .get("/api/v1/miot-calendar/slot-managers/runs")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    // ── Deactivate ────────────────────────────────────────────────────────

    @Test
    @Order(11)
    void testDeactivateManager() {
        given()
            .when()
            .delete("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(204);

        given()
            .when()
            .get("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    // ── Validation errors ─────────────────────────────────────────────────

    @Test
    @Order(12)
    void testCreateManagerMissingCalendarId() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "daysInAdvance": 30
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(13)
    void testCreateManagerUnknownCalendar() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "calendarId": "00000000-0000-0000-0000-000000000000"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(14)
    void testCreateManagerDuplicate() {
        // Second manager for same calendar should fail
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s"
                }
                """, calendarId))
            .when()
            .post("/api/v1/miot-calendar/slot-managers")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(15)
    void testUpdateManagerInvalidReprocess() {
        // reprocessFrom without reprocessTo
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "reprocessFrom": "%s"
                }
                """, LocalDate.now()))
            .when()
            .put("/api/v1/miot-calendar/slot-managers/" + managerId)
            .then()
            .statusCode(400);
    }

    @Test
    @Order(16)
    void testGetNonExistentManager() {
        given()
            .when()
            .get("/api/v1/miot-calendar/slot-managers/00000000-0000-0000-0000-000000000000")
            .then()
            .statusCode(404);
    }

    @Test
    @Order(17)
    void testRunNonExistentManager() {
        given()
            .when()
            .post("/api/v1/miot-calendar/slot-managers/00000000-0000-0000-0000-000000000000/run")
            .then()
            .statusCode(404);
    }
}
