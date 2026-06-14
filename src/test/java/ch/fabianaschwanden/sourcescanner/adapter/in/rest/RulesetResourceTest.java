package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** REST + RBAC der Rulesets (WR-90..96): Lesen ab Viewer, Pflege nur Admin. */
@QuarkusTest
class RulesetResourceTest {

    private static String body(String name) {
        return """
                {"name":"%s","enforcement":"ACTIVE","global":true,"repoNames":[],
                 "rules":[{"ruleId":"email","enabled":false,"severity":"LOW","matchMode":"LIST",
                   "dataSourceName":"crm"}]}""".formatted(name);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_lesen_aber_nicht_anlegen() {
        given().when().get("/api/rulesets").then().statusCode(200);
        given().contentType("application/json").body(body("rs-v"))
                .when().post("/api/rulesets").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_legt_ruleset_mit_regel_override_an() {
        given().contentType("application/json").body(body("rs-" + java.util.UUID.randomUUID()))
                .when().post("/api/rulesets").then().statusCode(200)
                .body("enforcement", Matchers.equalTo("ACTIVE"))
                .body("global", Matchers.equalTo(true))
                .body("rules[0].ruleId", Matchers.equalTo("email"))
                .body("rules[0].enabled", Matchers.equalTo(false))
                .body("rules[0].matchMode", Matchers.equalTo("LIST"))
                .body("rules[0].dataSourceName", Matchers.equalTo("crm"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void verfuegbare_regeln_listen_email_pattern() {
        given().when().get("/api/detectors/rules").then().statusCode(200)
                .body("id", Matchers.hasItem("email"));
    }
}
