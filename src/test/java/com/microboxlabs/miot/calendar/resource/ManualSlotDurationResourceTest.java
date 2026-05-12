package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST-level coverage for the MANUAL slot-generation mode wired through CalendarResource:
 * explicit slot duration vs. seeded-from-capacity, the [5, windowMinutes] validation bounds, the
 * derived totalSlots/bookableSlots on the response, AUTO↔MANUAL round-tripping, and the fact that a
 * parallelism change leaves a MANUAL window's admin-set duration alone (only AUTO windows re-derive).
 */
@QuarkusTest
class ManualSlotDurationResourceTest {

    private static final String CALENDARS_PATH = "/api/v1/miot-calendar/calendars";
    private static final LocalDate VALID_FROM = LocalDate.now();

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    private String createCalendar(String code, int parallelism) {
        return given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                { "code": "%s", "name": "%s", "parallelism": %d }
                """, code, code, parallelism))
            .when().post(CALENDARS_PATH)
            .then().statusCode(201)
            .extract().path("id");
    }

    private static String timeWindowBody(String generationMode, Integer slotDurationMinutes,
                                         int startHour, int endHour, int capacity) {
        String duration = slotDurationMinutes == null ? "" : "\"slotDurationMinutes\": " + slotDurationMinutes + ",";
        return String.format("""
            {
                "name": "Loading Window",
                "slotGenerationMode": "%s",
                %s
                "startHour": %d,
                "endHour": %d,
                "capacity": %d,
                "daysOfWeek": "1,2,3,4,5",
                "validFrom": "%s"
            }
            """, generationMode, duration, startHour, endHour, capacity, VALID_FROM);
    }

    private io.restassured.response.Response postTimeWindow(String calendarId, String body) {
        return given().contentType(ContentType.JSON).body(body)
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .thenReturn();
    }

    @Test
    void createsManualWindowWithExplicitDurationAndDerivedSlotCounts() {
        String calendarId = createCalendar("msd-explicit", 1);
        // 4h window, 10-min slots → 24 slots fit; capacity 20, parallelism 1 → 20 OPEN, 4 OVERFLOW.
        given().contentType(ContentType.JSON).body(timeWindowBody("MANUAL", 10, 8, 12, 20))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201)
            .body("slotGenerationMode", equalTo("MANUAL"))
            .body("slotDurationMinutes", equalTo(10))
            .body("totalSlots", equalTo(24))
            .body("bookableSlots", equalTo(20));
    }

    @Test
    void seedsManualSlotDurationFromCapacityModelWhenOmitted() {
        String calendarId = createCalendar("msd-seeded", 1);
        // No slotDurationMinutes → seeded = 240min / ceil(4/1) = 60.
        given().contentType(ContentType.JSON).body(timeWindowBody("MANUAL", null, 8, 12, 4))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201)
            .body("slotGenerationMode", equalTo("MANUAL"))
            .body("slotDurationMinutes", equalTo(60))
            .body("totalSlots", equalTo(4))
            .body("bookableSlots", equalTo(4));
    }

    @Test
    void rejectsManualSlotDurationBelowMinimum() {
        String calendarId = createCalendar("msd-too-short", 1);
        postTimeWindow(calendarId, timeWindowBody("MANUAL", 3, 8, 12, 4))
            .then().statusCode(400);
    }

    @Test
    void rejectsManualSlotDurationLongerThanWindow() {
        String calendarId = createCalendar("msd-too-long", 1);
        // 8-12 = 240 min; 300 > 240 → rejected.
        postTimeWindow(calendarId, timeWindowBody("MANUAL", 300, 8, 12, 4))
            .then().statusCode(400);
    }

    @Test
    void roundTripsBetweenAutoAndManual() {
        String calendarId = createCalendar("msd-roundtrip", 1);
        String twId = given().contentType(ContentType.JSON).body(timeWindowBody("AUTO", null, 8, 12, 4))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201)
            .body("slotGenerationMode", equalTo("AUTO"))
            .body("slotDurationMinutes", equalTo(60))   // 240 / ceil(4/1)
            .extract().path("id");

        String twPath = CALENDARS_PATH + "/" + calendarId + "/time-windows/" + twId;

        // AUTO → MANUAL with no duration: seeded from the (current) derived value.
        given().contentType(ContentType.JSON).body("{ \"slotGenerationMode\": \"MANUAL\" }")
            .when().put(twPath)
            .then().statusCode(200)
            .body("slotGenerationMode", equalTo("MANUAL"))
            .body("slotDurationMinutes", equalTo(60));

        // MANUAL: set an explicit duration.
        given().contentType(ContentType.JSON).body("{ \"slotGenerationMode\": \"MANUAL\", \"slotDurationMinutes\": 20 }")
            .when().put(twPath)
            .then().statusCode(200)
            .body("slotDurationMinutes", equalTo(20))
            .body("totalSlots", equalTo(12))     // 240 / 20
            .body("bookableSlots", equalTo(4));  // ceil(4/1), capped at 12

        // MANUAL → AUTO: duration re-derived, OVERFLOW gone (bookableSlots == totalSlots).
        given().contentType(ContentType.JSON).body("{ \"slotGenerationMode\": \"AUTO\" }")
            .when().put(twPath)
            .then().statusCode(200)
            .body("slotGenerationMode", equalTo("AUTO"))
            .body("slotDurationMinutes", equalTo(60))
            .body("totalSlots", equalTo(4))
            .body("bookableSlots", equalTo(4));
    }

    @Test
    void parallelismChangeLeavesManualWindowDurationUntouched() {
        String calendarId = createCalendar("msd-par-keep", 1);
        // MANUAL 30-min slots over a 4h window: 8 slots fit; capacity 4, parallelism 1 → 4 OPEN + 4 OVERFLOW.
        given().contentType(ContentType.JSON).body(timeWindowBody("MANUAL", 30, 8, 12, 4))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201)
            .body("slotDurationMinutes", equalTo(30))
            .body("totalSlots", equalTo(8))
            .body("bookableSlots", equalTo(4));

        // Bump parallelism — a MANUAL window keeps its duration; only bookableSlots re-lays.
        given().contentType(ContentType.JSON).body("{ \"parallelism\": 2 }")
            .when().put(CALENDARS_PATH + "/" + calendarId)
            .then().statusCode(200).body("parallelism", equalTo(2));

        given().when().get(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(200)
            .body("[0].slotGenerationMode", equalTo("MANUAL"))
            .body("[0].slotDurationMinutes", equalTo(30))  // unchanged
            .body("[0].totalSlots", equalTo(8))            // unchanged
            .body("[0].bookableSlots", equalTo(2));        // ceil(4/2)
    }
}
