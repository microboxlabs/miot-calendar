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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A booking holds a seat in every status but CANCELLED. Cancelling in place —
 * the status patch that keeps the row as history instead of deleting it — gives
 * the seat back to the slot and to the time window's daily cap.
 */
@QuarkusTest
class BookingCancelCapacityTest {

    private static final String CALENDARS = "/api/v1/miot-calendar/calendars";
    private static final String SLOTS = "/api/v1/miot-calendar/slots";
    private static final String BOOKINGS = "/api/v1/miot-calendar/bookings";
    private static final LocalDate SLOT_DATE = LocalDate.now().plusDays(1);

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    /**
     * A MANUAL 08–12 window with 30-minute slots: 8 slots, each holding up to
     * parallelism (2) bookings, but only 4 bookings for the whole day.
     */
    private String createCalendar(String code) {
        String calendarId = given().contentType(ContentType.JSON)
            .body(String.format("""
                { "code": "%s", "name": "%s", "parallelism": 2 }
                """, code, code))
            .when().post(CALENDARS)
            .then().statusCode(201)
            .extract().path("id");

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
            .when().post(CALENDARS + "/" + calendarId + "/time-windows")
            .then().statusCode(201);

        given().contentType(ContentType.JSON)
            .body(String.format("""
                { "calendarId": "%s", "startDate": "%s", "endDate": "%s" }
                """, calendarId, SLOT_DATE, SLOT_DATE))
            .when().post(SLOTS + "/generate")
            .then().statusCode(200);

        return calendarId;
    }

    private Response book(String calendarId, String resourceId, int hour, int minutes) {
        return given().contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": { "id": "%s", "type": "SERVICE" },
                    "slot": { "date": "%s", "hour": %d, "minutes": %d }
                }
                """, calendarId, resourceId, SLOT_DATE, hour, minutes))
            .when().post(BOOKINGS)
            .thenReturn();
    }

    private String bookOk(String calendarId, String resourceId, int hour, int minutes) {
        return book(calendarId, resourceId, hour, minutes)
            .then().statusCode(201)
            .extract().path("id");
    }

    private void cancelInPlace(String bookingId) {
        given().contentType(ContentType.JSON)
            .body("""
                { "status": "CANCELLED" }
                """)
            .when().put(BOOKINGS + "/" + bookingId)
            .then().statusCode(200)
            .body("status", equalTo("CANCELLED"));
    }

    private int occupancyOf(String calendarId, int hour, int minutes) {
        return given()
            .when().get(SLOTS + "?calendarId=" + calendarId
                + "&startDate=" + SLOT_DATE + "&endDate=" + SLOT_DATE)
            .then().statusCode(200)
            .extract().path(String.format(
                "data.find { it.slotHour == %d && it.slotMinutes == %d }.currentOccupancy",
                hour, minutes));
    }

    private Response availableSlots(String calendarId) {
        return given()
            .when().get(SLOTS + "?calendarId=" + calendarId
                + "&startDate=" + SLOT_DATE + "&endDate=" + SLOT_DATE + "&available=true")
            .thenReturn();
    }

    @Test
    void cancellingInPlaceFreesTheSlotSeat() {
        String calendarId = createCalendar("cancel-slot-seat");

        bookOk(calendarId, "C1", 8, 0);
        String second = bookOk(calendarId, "C2", 8, 0);

        // 08:00 is at parallelism (2/2), so a third booking there is rejected.
        book(calendarId, "C3", 8, 0).then().statusCode(409);

        cancelInPlace(second);

        assertEquals(1, occupancyOf(calendarId, 8, 0));
        bookOk(calendarId, "C3", 8, 0);
    }

    @Test
    void cancellingInPlaceFreesTheWindowDayCap() {
        String calendarId = createCalendar("cancel-window-cap");

        bookOk(calendarId, "W1", 8, 0);
        bookOk(calendarId, "W2", 8, 30);
        bookOk(calendarId, "W3", 9, 0);
        String fourth = bookOk(calendarId, "W4", 9, 30);

        // Window at 4/4: a create into an empty slot is rejected.
        book(calendarId, "W5", 10, 0).then().statusCode(409);

        cancelInPlace(fourth);

        bookOk(calendarId, "W5", 10, 0);
    }

    @Test
    void aWindowFullOfCancelledBookingsStillListsItsSlotsAsAvailable() {
        String calendarId = createCalendar("cancel-availability");

        String first = bookOk(calendarId, "A1", 8, 0);
        String second = bookOk(calendarId, "A2", 8, 30);
        String third = bookOk(calendarId, "A3", 9, 0);
        String fourth = bookOk(calendarId, "A4", 9, 30);

        // At the day cap the availability listing drops every slot of the window.
        availableSlots(calendarId)
            .then().statusCode(200)
            .body("data.slotHour", not(hasItem(10)));

        cancelInPlace(first);
        cancelInPlace(second);
        cancelInPlace(third);
        cancelInPlace(fourth);

        availableSlots(calendarId)
            .then().statusCode(200)
            .body("data.slotHour", hasItem(10));
    }

    @Test
    void deletingAnAlreadyCancelledBookingDoesNotDoubleRelease() {
        String calendarId = createCalendar("cancel-then-delete");

        bookOk(calendarId, "D1", 8, 0);
        String second = bookOk(calendarId, "D2", 8, 0);

        cancelInPlace(second);
        given().when().delete(BOOKINGS + "/" + second).then().statusCode(204);

        // D1 still holds its seat: one release per booking, not two.
        assertEquals(1, occupancyOf(calendarId, 8, 0));
    }

    @Test
    void movingACancelledBookingTakesASeatAtTheTargetOnly() {
        String calendarId = createCalendar("cancel-then-move");

        String first = bookOk(calendarId, "M1", 8, 0);
        cancelInPlace(first);
        assertEquals(0, occupancyOf(calendarId, 8, 0));

        // The move resets the booking to PLANNED, so it takes a seat at the target. The source
        // gave its seat back when the booking was cancelled and must not give a second one.
        given().contentType(ContentType.JSON)
            .body(String.format("""
                { "slot": { "date": "%s", "hour": 10, "minutes": 0 } }
                """, SLOT_DATE))
            .when().post(BOOKINGS + "/" + first + "/move")
            .then().statusCode(200)
            .body("status", equalTo("PLANNED"));

        assertEquals(0, occupancyOf(calendarId, 8, 0));
        assertEquals(1, occupancyOf(calendarId, 10, 0));
    }

    @Test
    void creatingABookingAlreadyCancelledTakesNoSeat() {
        String calendarId = createCalendar("cancel-on-create");

        given().contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "resource": { "id": "X1", "type": "SERVICE" },
                    "slot": { "date": "%s", "hour": 8, "minutes": 0 },
                    "status": "CANCELLED"
                }
                """, calendarId, SLOT_DATE))
            .when().post(BOOKINGS)
            .then().statusCode(201)
            .body("status", equalTo("CANCELLED"));

        assertEquals(0, occupancyOf(calendarId, 8, 0));
    }

    @Test
    void patchByResourceAlsoFreesCapacity() {
        String calendarId = createCalendar("cancel-by-resource");

        bookOk(calendarId, "P1", 8, 0);
        bookOk(calendarId, "P2", 8, 0);
        book(calendarId, "P3", 8, 0).then().statusCode(409);

        given().contentType(ContentType.JSON)
            .body("""
                { "status": "CANCELLED" }
                """)
            .when().patch(BOOKINGS + "/resource/P2?calendarId=" + calendarId)
            .then().statusCode(200);

        assertEquals(1, occupancyOf(calendarId, 8, 0));
        bookOk(calendarId, "P3", 8, 0);
    }
}
