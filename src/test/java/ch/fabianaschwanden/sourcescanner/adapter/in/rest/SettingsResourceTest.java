package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Settings-REST + RBAC (WR-15/31): Lesen Viewer, Ändern nur Admin; secretRefs ohne Klartext. */
@QuarkusTest
class SettingsResourceTest {

    private static final String SETTINGS = """
            {"generalNotificationEmail":"sec@firma.ch","defaultFailOn":"CRITICAL",
             "defaultScanMode":"full","retentionDays":90,"secretRefs":[]}
            """;

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_liest_settings() {
        given().when().get("/api/settings").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_settings_nicht_aendern() {
        given().contentType(ContentType.JSON).body(SETTINGS)
                .when().put("/api/settings").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_aendert_settings() {
        given().contentType(ContentType.JSON).body(SETTINGS)
                .when().put("/api/settings")
                .then().statusCode(200)
                .body("defaultFailOn", equalTo("CRITICAL"))
                .body("retentionDays", equalTo(90));
    }
}
