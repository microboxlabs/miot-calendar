package com.microboxlabs.miot.calendar.resource;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-user", roles = {})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalendarGroupResourceTest {

    @TestHTTPResource
    URL url;

    private String groupId;
    private String calendarId;

    @BeforeEach
    void setupRestAssured() {
        RestAssured.baseURI = url.toString();
        RestAssured.port = url.getPort();
    }

    @Test
    @Order(1)
    void testCreateGroup() {
        groupId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "warehouse-south",
                    "name": "Warehouse South",
                    "description": "Calendars in Warehouse South area"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/groups")
            .then()
            .statusCode(201)
            .body("code", equalTo("warehouse-south"))
            .body("name", equalTo("Warehouse South"))
            .body("active", equalTo(true))
            .extract()
            .path("id");
    }

    @Test
    @Order(2)
    void testListGroups() {
        given()
            .when()
            .get("/api/v1/miot-calendar/groups")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(3)
    void testGetGroupById() {
        given()
            .when()
            .get("/api/v1/miot-calendar/groups/" + groupId)
            .then()
            .statusCode(200)
            .body("id", equalTo(groupId))
            .body("code", equalTo("warehouse-south"));
    }

    @Test
    @Order(4)
    void testUpdateGroup() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Warehouse South Updated"
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/groups/" + groupId)
            .then()
            .statusCode(200)
            .body("name", equalTo("Warehouse South Updated"))
            .body("code", equalTo("warehouse-south"));
    }

    @Test
    @Order(5)
    void testCreateCalendarWithGroup() {
        calendarId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "grp-test-calendar",
                    "name": "Group Test Calendar",
                    "timezone": "America/Santiago",
                    "groups": ["warehouse-south"]
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(201)
            .body("code", equalTo("grp-test-calendar"))
            .body("groups.size()", equalTo(1))
            .body("groups[0].code", equalTo("warehouse-south"))
            .extract()
            .path("id");
    }

    @Test
    @Order(6)
    void testListCalendarsByGroupCode() {
        given()
            .queryParam("groupCode", "warehouse-south")
            .when()
            .get("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("code", hasItem("grp-test-calendar"));
    }

    @Test
    @Order(7)
    void testUpdateCalendarRemoveGroups() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "groups": []
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("groups.size()", equalTo(0));
    }

    @Test
    @Order(8)
    void testUpdateCalendarRestoreGroups() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "groups": ["warehouse-south"]
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("groups.size()", equalTo(1))
            .body("groups[0].code", equalTo("warehouse-south"));
    }

    @Test
    @Order(9)
    void testUpdateCalendarNullGroupsIsNoOp() {
        // PUT with no "groups" field → groups field absent → null → no change
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Group Test Calendar Renamed"
                }
                """)
            .when()
            .put("/api/v1/miot-calendar/calendars/" + calendarId)
            .then()
            .statusCode(200)
            .body("name", equalTo("Group Test Calendar Renamed"))
            .body("groups.size()", equalTo(1))
            .body("groups[0].code", equalTo("warehouse-south"));
    }

    @Test
    @Order(10)
    void testDeactivateGroup() {
        given()
            .when()
            .delete("/api/v1/miot-calendar/groups/" + groupId)
            .then()
            .statusCode(204);

        // Verify the group is now inactive
        given()
            .when()
            .get("/api/v1/miot-calendar/groups/" + groupId)
            .then()
            .statusCode(200)
            .body("active", equalTo(false));
    }

    @Test
    @Order(11)
    void testCreateGroupDuplicateCode() {
        // Recreate the group first so we have something to duplicate
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "duplicate-group",
                    "name": "Duplicate Group"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/groups")
            .then()
            .statusCode(201);

        // Now try to create again with the same code
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "duplicate-group",
                    "name": "Another Group"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/groups")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(12)
    void testCreateGroupMissingCode() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Missing Code Group"
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/groups")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(13)
    void testCreateCalendarWithUnknownGroupCode() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "code": "unknown-grp-calendar",
                    "name": "Unknown Group Calendar",
                    "groups": ["nonexistent-group"]
                }
                """)
            .when()
            .post("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(14)
    void testListCalendarsByNonexistentGroupCode() {
        given()
            .queryParam("groupCode", "nonexistent-group-xyz")
            .when()
            .get("/api/v1/miot-calendar/calendars")
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }
}
