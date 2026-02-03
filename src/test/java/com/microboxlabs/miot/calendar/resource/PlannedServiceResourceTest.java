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

/**
 * Tests for the backward-compatible planned-services endpoint
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlannedServiceResourceTest {

    @TestHTTPResource
    URL url;

    private String calendarId;
    private LocalDate slotDate;
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

        slotDate = LocalDate.now().plusDays(2);

        // Create a calendar
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "planned-svc-test-calendar",
                    "name": "Planned Service Test Calendar"
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
                    "name": "Planned Service Test Window",
                    "startHour": 6,
                    "endHour": 22,
                    "slotDurationMinutes": 30,
                    "capacityPerSlot": 5,
                    "daysOfWeek": "MON,TUE,WED,THU,FRI,SAT,SUN",
                    "validFrom": "%s"
                }
                """, LocalDate.now().toString()))
            .when()
            .post("/api/calendars/" + calendarId + "/time-windows")
            .then()
            .statusCode(201);

        // Generate slots
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "calendarId": "%s",
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, calendarId, slotDate, slotDate.plusDays(30)))
            .when()
            .post("/api/slots/generate")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(1)
    void testCreatePlannedService() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("calendarId", calendarId)
            .body(String.format("""
                {
                    "service": {
                        "id": "SRV-PLANNED-001",
                        "cliente": "Acme Corp",
                        "origen": "Santiago",
                        "destino": "Valparaiso",
                        "tipoViaje": "Sider",
                        "ocupacion": 85,
                        "permanencia": "24h",
                        "leadTime": {
                            "total_lineasoc_cumplen": 10,
                            "total_lineasoc_incumplen": 2,
                            "lineasoc_pctn_cumplimiento": 83
                        },
                        "eta": "2025-01-15T14:30:00Z",
                        "incidencias": [],
                        "observaciones": "Carga fragil",
                        "prioridad": 1
                    },
                    "slot": {
                        "date": "%s",
                        "hour": 14,
                        "minutes": 30
                    }
                }
                """, slotDate))
            .when()
            .post("/api/planned-services")
            .then()
            .statusCode(201)
            .body("service.id", equalTo("SRV-PLANNED-001"))
            .body("service.cliente", equalTo("Acme Corp"))
            .body("slot.hour", equalTo(14))
            .body("slot.minutes", equalTo(30));
    }

    @Test
    @Order(2)
    void testListPlannedServices() {
        given()
            .queryParam("startDate", slotDate.toString())
            .queryParam("endDate", slotDate.plusDays(30).toString())
            .when()
            .get("/api/planned-services")
            .then()
            .statusCode(200)
            .body("data.size()", greaterThan(0))
            .body("data[0].service.id", notNullValue());
    }

    @Test
    void testCreatePlannedServiceRequiresCalendarId() {
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "service": {
                        "id": "SRV-NO-CALENDAR"
                    },
                    "slot": {
                        "date": "%s",
                        "hour": 10,
                        "minutes": 0
                    }
                }
                """, slotDate))
            .when()
            .post("/api/planned-services")
            .then()
            .statusCode(400);
    }

    @Test
    void testCreatePlannedServiceValidation() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("calendarId", calendarId)
            .body("""
                {
                    "service": {},
                    "slot": {
                        "date": "2025-01-15",
                        "hour": 10,
                        "minutes": 0
                    }
                }
                """)
            .when()
            .post("/api/planned-services")
            .then()
            .statusCode(400);
    }
}
