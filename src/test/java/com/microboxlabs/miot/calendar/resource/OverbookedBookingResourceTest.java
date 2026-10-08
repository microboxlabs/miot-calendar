package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * An overbooked booking (allowOverbooking=true) sits outside capacity: it does not count in
 * the slot's occupancy nor in a MANUAL window's daily cap, so it never takes room from a
 * planner. Moving it makes it an ordinary booking.
 */
@QuarkusTest
class OverbookedBookingResourceTest {

    private static final String CALENDARS_PATH = "/api/v1/miot-calendar/calendars";
    private static final String BOOKINGS_PATH = "/api/v1/miot-calendar/bookings";
    private static final String SLOTS_PATH = "/api/v1/miot-calendar/slots";
    private static final LocalDate VALID_FROM = LocalDate.now();
    private static final LocalDate DATE = VALID_FROM.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    /** Parallelism 1 (one booking per slot), MANUAL 60-min slots from 8 to endHour, daily cap. */
    private String calendar(String code, int endHour, int windowCapacity) {
        String calendarId = given().contentType(ContentType.JSON)
            .body(String.format("""
                { "code": "%s", "name": "%s", "parallelism": 1 }
                """, code, code))
            .when().post(CALENDARS_PATH)
            .then().statusCode(201)
            .extract().path("id");

        given().contentType(ContentType.JSON).body(String.format("""
            {
                "name": "Loading Window",
                "slotGenerationMode": "MANUAL",
                "slotDurationMinutes": 60,
                "startHour": 8,
                "endHour": %d,
                "capacity": %d,
                "daysOfWeek": "1,2,3,4,5",
                "validFrom": "%s"
            }
            """, endHour, windowCapacity, VALID_FROM))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201);

        generateSlots(calendarId, false);
        return calendarId;
    }

    private static void generateSlots(String calendarId, boolean reprocess) {
        given().contentType(ContentType.JSON).body(String.format("""
            { "calendarId": "%s", "startDate": "%s", "endDate": "%s", "reprocess": %b }
            """, calendarId, DATE, DATE, reprocess))
            .when().post(SLOTS_PATH + "/generate")
            .then().statusCode(200);
    }

    private static io.restassured.response.ValidatableResponse book(String calendarId, String resourceId,
                                                                    int hour, boolean allowOverbooking) {
        return given().contentType(ContentType.JSON).body(String.format("""
            {
                "calendarId": "%s",
                "resource": { "id": "%s", "type": "SERVICE" },
                "slot": { "date": "%s", "hour": %d, "minutes": 0 },
                "allowOverbooking": %b
            }
            """, calendarId, resourceId, DATE, hour, allowOverbooking))
            .when().post(BOOKINGS_PATH)
            .then();
    }

    private static io.restassured.response.ValidatableResponse move(String bookingId, int hour) {
        return given().contentType(ContentType.JSON).body(String.format("""
            { "slot": { "date": "%s", "hour": %d, "minutes": 0 } }
            """, DATE, hour))
            .when().post(BOOKINGS_PATH + "/" + bookingId + "/move")
            .then();
    }

    private static int occupancy(String calendarId, int hour) {
        return given()
            .queryParam("calendarId", calendarId)
            .queryParam("startDate", DATE.toString())
            .queryParam("endDate", DATE.toString())
            .when().get(SLOTS_PATH)
            .then().statusCode(200)
            .extract().path("data.find { it.slotHour == " + hour + " }.currentOccupancy");
    }

    @Test
    void overbookedBookingLeavesCapacityToThePlanner() {
        String calendarId = calendar("ob-capacity", 10, 1);

        book(calendarId, "auto-1", 8, true).statusCode(201).body("overbooked", equalTo(true));
        assertOccupancy(calendarId, 8, 0);

        // Slot and window still have room for the planner
        book(calendarId, "manual-1", 8, false).statusCode(201).body("overbooked", equalTo(false));
        assertOccupancy(calendarId, 8, 1);

        // The planner's booking does count: the window cap of 1 is now reached
        book(calendarId, "manual-2", 9, false).statusCode(409);
    }

    @Test
    void movingAnOverbookedBookingMakesItOrdinary() {
        String calendarId = calendar("ob-move", 11, 2);
        String autoId = book(calendarId, "auto-1", 8, true).statusCode(201).extract().path("id");
        book(calendarId, "manual-1", 9, false).statusCode(201);

        // Slot 9 is full (parallelism 1): a moved booking must fit like a new one
        move(autoId, 9).statusCode(409);

        move(autoId, 10).statusCode(200).body("overbooked", equalTo(false));
        assertOccupancy(calendarId, 8, 0);
        assertOccupancy(calendarId, 10, 1);

        // Two ordinary bookings now fill the window cap of 2
        book(calendarId, "manual-2", 8, false).statusCode(409);
    }

    @Test
    void cancellingAnOverbookedBookingKeepsOccupancy() {
        String calendarId = calendar("ob-cancel", 10, 2);
        String autoId = book(calendarId, "auto-1", 8, true).statusCode(201).extract().path("id");
        book(calendarId, "manual-1", 8, false).statusCode(201);

        given().when().delete(BOOKINGS_PATH + "/" + autoId).then().statusCode(204);

        assertOccupancy(calendarId, 8, 1);
    }

    @Test
    void reprocessKeepsASlotThatOnlyHasAnOverbookedBooking() {
        String calendarId = calendar("ob-reprocess", 10, 1);
        String autoId = book(calendarId, "auto-1", 8, true).statusCode(201).extract().path("id");

        generateSlots(calendarId, true);

        given().when().get(BOOKINGS_PATH + "/" + autoId)
            .then().statusCode(200)
            .body("slot.hour", equalTo(8));
    }

    private static void assertOccupancy(String calendarId, int hour, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, occupancy(calendarId, hour),
            "currentOccupancy of slot " + hour);
    }
}
