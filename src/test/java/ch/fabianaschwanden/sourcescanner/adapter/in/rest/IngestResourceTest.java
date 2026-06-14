package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Ingest-Endpoint für CI-Läufe (IR-22..25): RBAC (nur Rolle ci), Persistenz mit trigger=CI und
 * Idempotenz über runRef.
 */
@QuarkusTest
class IngestResourceTest {

    private static String body(String runRef) {
        return """
                {"repoId":"repo-ci-%s","mode":"diff","status":"COMPLETED","runRef":"%s",
                 "pipelineUrl":"https://ci/pipe/1","commit":"abc123","branch":"main","actor":"ci-bot",
                 "findings":[{"detectorId":"secret.regex-ruleset","category":"SECRET","severity":"HIGH",
                   "ruleId":"aws","file":"src/A.java","line":1,"redactedMatch":"AKIA****MPLE",
                   "fingerprint":"fp-1","verified":false}]}""".formatted(runRef, runRef);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_nicht_einliefern() {
        given().contentType("application/json").body(body("v1"))
                .when().post("/api/ingest").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "operator-user", roles = "operator")
    void operator_darf_nicht_einliefern() {
        given().contentType("application/json").body(body("o1"))
                .when().post("/api/ingest").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "ci-bot", roles = "ci")
    void ci_liefert_ein_und_lauf_traegt_herkunft_ci() {
        String runRef = "pipe-" + java.util.UUID.randomUUID();
        given().contentType("application/json").body(body(runRef))
                .when().post("/api/ingest").then().statusCode(200)
                .body("trigger", Matchers.equalTo("CI"))
                .body("findingCount", Matchers.equalTo(1))
                .body("ciBranch", Matchers.equalTo("main"));
    }

    @Test
    @TestSecurity(user = "ci-bot", roles = "ci")
    void erneute_einlieferung_derselben_runRef_ist_idempotent() {
        String runRef = "pipe-" + java.util.UUID.randomUUID();
        String first = given().contentType("application/json").body(body(runRef))
                .when().post("/api/ingest").then().statusCode(200).extract().path("id");
        String second = given().contentType("application/json").body(body(runRef))
                .when().post("/api/ingest").then().statusCode(200).extract().path("id");
        org.junit.jupiter.api.Assertions.assertEquals(first, second, "gleiche runRef ⇒ gleiche Scan-ID");
    }
}
