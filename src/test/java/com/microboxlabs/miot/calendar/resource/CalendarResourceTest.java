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
        return createCalendar(code, origin, null, isDefault);
    }

    /**
     * Create a calendar, returning its id. Either key may be null; both null
     * means no filter at all, which is the catch-all default's shape.
     */
    private String createCalendar(String code, String origin, String serviceType, boolean isDefault) {
        StringBuilder keys = new StringBuilder();
        if (origin != null) {
            keys.append("\"origin\": \"").append(origin).append("\"");
        }
        if (serviceType != null) {
            if (keys.length() > 0) {
                keys.append(", ");
            }
            keys.append("\"serviceType\": \"").append(serviceType).append("\"");
        }
        String filter = keys.length() == 0 ? "null" : "{" + keys + "}";
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
            .statusCode(204);
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
            .statusCode(204);
    }

    @Test
    void testCalendarsAreNotDefaultUnlessAsked() {
        String id = createCalendar("default-implicit", "IMP", false);
        assertDefaultFlag(id, false);
    }

    // --- Defaults scoped by (origin, service type) ---

    @Test
    void testOneOriginCanHoldADefaultPerServiceType() {
        // The whole point of widening the key: before V17 the unique index
        // allowed one default per origin, so these two could not coexist.
        String untyped = createCalendar("stype-coex-plain", "SCA", null, true);
        String otr = createCalendar("stype-coex-otr", "SCA", "otr", true);

        assertDefaultFlag(untyped, true);
        assertDefaultFlag(otr, true);
    }

    @Test
    void testTypedDefaultWinsOverTheOriginsUntypedOne() {
        createCalendar("stype-win-plain", "SCB", null, true);
        String otr = createCalendar("stype-win-otr", "SCB", "otr", true);

        given()
            .queryParam("origin", "SCB")
            .queryParam("serviceType", "otr")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(otr));
    }

    @Test
    void testUntypedOriginDefaultAnswersForAnUnclaimedType() {
        // The rung that makes this migration free: every default predating
        // service types carries none, so it keeps answering for v — and for
        // any other type nobody has claimed yet.
        String untyped = createCalendar("stype-fall-plain", "SCC", null, true);
        createCalendar("stype-fall-otr", "SCC", "otr", true);

        given()
            .queryParam("origin", "SCC")
            .queryParam("serviceType", "ote")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(untyped));
    }

    @Test
    void testTypeOnlyDefaultServesEveryOriginThatDoesNotClaimTheType() {
        String shared = createCalendar("stype-shared-otr", null, "otr", true);

        given()
            .queryParam("origin", "SCD-" + System.nanoTime())
            .queryParam("serviceType", "otr")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(shared));
    }

    @Test
    void testPromotingATypedDefaultLeavesTheUntypedOneAlone() {
        // Demotion is keyed on the pair. Keyed on the origin alone, claiming
        // the otr default would silently unset a calendar nobody touched.
        String untyped = createCalendar("stype-dem-plain", "SCE", null, true);
        String otrFirst = createCalendar("stype-dem-otr-1", "SCE", "otr", true);
        String otrSecond = createCalendar("stype-dem-otr-2", "SCE", "otr", true);

        assertDefaultFlag(untyped, true);
        assertDefaultFlag(otrFirst, false);
        assertDefaultFlag(otrSecond, true);
    }

    @Test
    void testServiceTypeIsStoredLowerCased() {
        // The unique index compares stored text, so OTR and otr must not be
        // able to claim the same origin's default twice.
        String id = createCalendar("stype-case", "SCF", "OTR", true);

        given()
            .when()
            .get("/api/v1/miot-calendar/calendars/" + id)
            .then()
            .statusCode(200)
            .body("filter.serviceType", equalTo("otr"));

        given()
            .queryParam("origin", "SCF")
            .queryParam("serviceType", "otr")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .body("id", equalTo(id));
    }

    @Test
    void testDefaultLookupEchoesTheServiceTypeItConsidered() {
        // The echo is how a caller tells this apart from a server too old to
        // know the parameter, which would answer 200 with the wrong calendar.
        createCalendar("stype-echo", "SCG", "otr", true);

        given()
            .queryParam("origin", "SCG")
            .queryParam("serviceType", "otr")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(200)
            .header("X-Resolved-Service-Type", equalTo("otr"));

        given()
            .queryParam("origin", "NOPE-" + System.nanoTime())
            .queryParam("serviceType", "ote")
            .when()
            .get("/api/v1/miot-calendar/calendars/default")
            .then()
            .statusCode(204)
            .header("X-Resolved-Service-Type", equalTo("ote"));
    }

    @Test
    void testUnknownFilterKeyIsRejected() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "stype-badkey",
                    "name": "stype-badkey",
                    "filter": {"tipoViaje": "Sider"}
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(400);
    }
}
