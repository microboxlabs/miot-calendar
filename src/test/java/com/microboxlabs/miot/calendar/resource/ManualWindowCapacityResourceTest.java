package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end coverage for the MANUAL time-window total-capacity cap: the slot grid is intentionally
 * larger than the window's {@code capacity}, every slot is bookable, and a booking is rejected
 * (anywhere in the window, even into a partially-full slot) once the day's total bookings reach
 * {@code capacity}. Cancelling a booking re-opens the window.
 */
@QuarkusTest
class ManualWindowCapacityResourceTest {

    private static final String CALENDARS_PATH = "/api/v1/miot-calendar/calendars";
    private static final String SLOTS_GENERATE_PATH = "/api/v1/miot-calendar/slots/generate";
    private static final String BOOKINGS_PATH = "/api/v1/miot-calendar/bookings";
    /** Tomorrow — matches the window's daysOfWeek "1..7" and is >= validFrom (today). */
    private static final LocalDate SLOT_DATE = LocalDate.now().plusDays(1);

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    private String createCalendarWithManualWindow(String code) {
        String calendarId = given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                { "code": "%s", "name": "%s", "parallelism": 2 }
                """, code, code))
            .when().post(CALENDARS_PATH)
            .then().statusCode(201)
            .extract().path("id");

        // A MANUAL 08-12 window (240 minutes) with 30-minute slots fits 8 slots; each slot holds
        // up to parallelism bookings (2 in this calendar). The window capacity is 4, so only 4
        // bookings total fit across the day — the 4 surplus cells stay empty.
        given().contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "name": "Capped Window",
                    "slotGenerationMode": "MANUAL",
                    "slotDurationMinutes": 30,
                    "startHour": 8,
                    "endHour": 12,
                    "capacity": 4,
                    "daysOfWeek": "1,2,3,4,5,6,7",
                    "validFrom": "%s"
                }
                """, LocalDate.now()))
            .when().post(CALENDARS_PATH + "/" + calendarId + "/time-windows")
            .then().statusCode(201)
            .body("totalSlots", equalTo(8))
            .body("bookableSlots", equalTo(8));

        given().contentType(ContentType.JSON)
            .body(String.format("""
                { "calendarId": "%s", "startDate": "%s", "endDate": "%s" }
                """, calendarId, SLOT_DATE, SLOT_DATE))
            .when().post(SLOTS_GENERATE_PATH)
            .then().statusCode(200);

        return calendarId;
    }

    private Response book(String calendarId, String resourceId, int hour, int minutes) {
        return given().contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": { "id": "%s", "type": "SERVICE", "label": "%s" },
                    "slot": { "date": "%s", "hour": %d, "minutes": %d }
                }
                """, calendarId, resourceId, resourceId, SLOT_DATE, hour, minutes))
            .when().post(BOOKINGS_PATH)
            .thenReturn();
    }

    private String bookOk(String calendarId, String resourceId, int hour, int minutes) {
        return book(calendarId, resourceId, hour, minutes)
            .then().statusCode(201)
            .extract().path("id");
    }

    @Test
    void rejectsBookingsBeyondWindowCapacityRegardlessOfSlot() {
        String calendarId = createCalendarWithManualWindow("mwc-reject");

        // 4 bookings scattered across the grid: 2 into the 08:00 slot (fills it), 1 @09:00, 1 @10:00.
        bookOk(calendarId, "R1", 8, 0);
        bookOk(calendarId, "R2", 8, 0);
        bookOk(calendarId, "R3", 9, 0);
        bookOk(calendarId, "R4", 10, 0);

        // 5th into a still-empty slot → window is full for the day.
        book(calendarId, "R5", 11, 0).then().statusCode(409);
        // 5th into a partially-full slot (09:00 has room: 1/2) → still rejected; the cap is on the
        // window's total bookings, in any slot, in any order.
        book(calendarId, "R6", 9, 30).then().statusCode(409);
    }

    @Test
    void cancellingABookingReopensTheWindow() {
        String calendarId = createCalendarWithManualWindow("mwc-reopen");

        bookOk(calendarId, "R1", 8, 0);
        bookOk(calendarId, "R2", 8, 30);
        bookOk(calendarId, "R3", 9, 0);
        String fourth = bookOk(calendarId, "R4", 9, 30);

        book(calendarId, "R5", 10, 0).then().statusCode(409);

        given().when().delete(BOOKINGS_PATH + "/" + fourth).then().statusCode(204);

        // A slot freed up — the next booking (anywhere) succeeds again.
        bookOk(calendarId, "R5", 10, 0);
    }
}
