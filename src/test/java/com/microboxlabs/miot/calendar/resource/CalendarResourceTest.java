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

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
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
            .body("hasSlotManager", equalTo(true))
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
            .body("code", equalTo("test-calendar"))
            .body("hasSlotManager", equalTo(true));
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
            .body("name", equalTo("Updated Test Calendar"))
            .body("hasSlotManager", equalTo(true));
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
                    "capacity": 8,
                    "daysOfWeek": "1,2,3,4,5",
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
            .body("active", equalTo(false))
            .body("hasSlotManager", equalTo(true));
    }

    @Test
    @Order(8)
    void testHardDeleteCalendar() {
        // Create a separate calendar for hard-delete testing
        String purgeCalendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "purge-test-calendar",
                    "name": "Purge Test Calendar",
                    "timezone": "America/Santiago"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Purge the calendar
        given()
            .when()
            .delete("/api/v1/miot-calendar/calendars/" + purgeCalendarId + "/purge")
            .then()
            .statusCode(204);

        // Verify the calendar is gone
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + purgeCalendarId)
            .then()
            .statusCode(404);
    }

    @Test
    @Order(9)
    void testHardDeleteNonExistentCalendar() {
        given()
            .when()
            .delete("/api/v1/miot-calendar/calendars/00000000-0000-0000-0000-000000000000/purge")
            .then()
            .statusCode(404);
    }

    @Test
    void testCreateCalendarWithoutAutoSlotManager() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "no-auto-mgr",
                    "name": "No Auto Manager",
                    "timezone": "UTC",
                    "autoSlotManager": false
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("code", equalTo("no-auto-mgr"))
            .body("hasSlotManager", equalTo(false));
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

    @Test
    void testCreateCalendarWithFilter() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "filter-create",
                    "name": "Filter Create",
                    "timezone": "America/Santiago",
                    "filter": {"origin": "ANF", "destination": "SCL"}
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("filter.origin", equalTo("ANF"))
            .body("filter.destination", equalTo("SCL"))
            .extract()
            .path("id");

        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("filter.origin", equalTo("ANF"))
            .body("filter.destination", equalTo("SCL"));
    }

    @Test
    void testUpdateCalendarFilterReplaceAndClear() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "filter-update",
                    "name": "Filter Update",
                    "timezone": "America/Santiago",
                    "filter": {"origin": "ANF"}
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Replace
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "filter": {"destination": "VAL"} }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("filter.origin", nullValue())
            .body("filter.destination", equalTo("VAL"));

        // Clear via empty object → null
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "filter": {} }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("filter", nullValue());

        // Omit field → no change (still null)
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "Filter Update Renamed" }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Filter Update Renamed"))
            .body("filter", nullValue());
    }

    @Test
    void testCreateCalendarRejectsUnknownFilterKey() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "filter-bad-key",
                    "name": "Filter Bad Key",
                    "timezone": "UTC",
                    "filter": {"carrier": "X"}
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreateCalendarBlankFilterValueDropped() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "filter-blank",
                    "name": "Filter Blank",
                    "timezone": "UTC",
                    "filter": {"origin": "ANF", "destination": "  "}
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("filter.origin", equalTo("ANF"))
            .body("filter.destination", nullValue());
    }

    /** Create a calendar, returning its id. Origin may be null for no filter. */
    private String createCalendar(String code, String origin, boolean isDefault) {
        String filter = origin == null ? "null" : "{\"origin\": \"" + origin + "\"}";
        return given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "%s",
                    "name": "%s",
                    "timezone": "UTC",
                    "filter": %s,
                    "isDefault": %s
                }
                """.formatted(code, code, filter, isDefault))
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("isDefault", equalTo(isDefault))
            .extract()
            .path("id");
    }

    private static void assertDefaultFlag(String id, boolean expected) {
        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("isDefault", equalTo(expected));
    }

    @Test
    void testDefaultCalendarIsResolvedByOrigin() {
        String id = createCalendar("default-por", "POR", true);

        given()
            .queryParam("origin", "POR")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("isDefault", equalTo(true));
    }

    @Test
    void testMarkingANewDefaultDemotesThePreviousOne() {
        // One per origin: the flag is a radio button, and the caller states an
        // intent rather than having to demote the incumbent first.
        String first = createCalendar("default-dem-1", "DEM", true);
        String second = createCalendar("default-dem-2", "DEM", true);

        assertDefaultFlag(first, false);
        assertDefaultFlag(second, true);

        given()
            .queryParam("origin", "DEM")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(second));
    }

    @Test
    void testDefaultsForDifferentOriginsCoexist() {
        String one = createCalendar("default-coex-1", "CXA", true);
        String two = createCalendar("default-coex-2", "CXB", true);

        assertDefaultFlag(one, true);
        assertDefaultFlag(two, true);
    }

    @Test
    void testUpdatePromotesAndDemotes() {
        String incumbent = createCalendar("default-upd-1", "UPD", true);
        String challenger = createCalendar("default-upd-2", "UPD", false);

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"isDefault": true}
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + challenger)
            .then()
            .statusCode(200)
            .body("isDefault", equalTo(true));

        assertDefaultFlag(incumbent, false);

        // And clearing it leaves the origin with no default at all, rather than
        // silently handing the flag back.
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"isDefault": false}
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + challenger)
            .then()
            .statusCode(200)
            .body("isDefault", equalTo(false));

        given()
            .queryParam("origin", "UPD")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(404);
    }

    @Test
    void testUnconfiguredOriginHasNoDefault() {
        // A 404 is the answer, not a failure: nothing is configured to receive
        // these bookings, so an integrator should create none.
        given()
            .queryParam("origin", "NOPE-" + System.nanoTime())
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(404);
    }

    @Test
    void testCalendarsAreNotDefaultUnlessAsked() {
        String id = createCalendar("default-implicit", "IMP", false);
        assertDefaultFlag(id, false);
    }
}
