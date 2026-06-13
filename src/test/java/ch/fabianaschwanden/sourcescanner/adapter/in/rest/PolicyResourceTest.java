package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Policy-REST + RBAC (FR-20, WR-31): Admin schreibt, Viewer liest, aber darf nicht schreiben. */
@QuarkusTest
class PolicyResourceTest {

    private static final String POLICY = """
            {"id":null,"orgUnit":"team-%s","failOn":"CRITICAL","failOnNewOnly":false,
             "softFail":false,"warnThreshold":"MEDIUM","enabledDetectorGroups":["secrets","pii"]}
            """;

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_legt_policy_an_und_liest() {
        given().contentType(ContentType.JSON).body(POLICY.formatted(System.nanoTime()))
                .when().post("/api/policies")
                .then().statusCode(201).body("failOn", equalTo("CRITICAL"));
        given().when().get("/api/policies").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_lesen_aber_nicht_schreiben() {
        given().when().get("/api/policies").then().statusCode(200);
        given().contentType(ContentType.JSON).body(POLICY.formatted(System.nanoTime()))
                .when().post("/api/policies").then().statusCode(403);
    }
}
