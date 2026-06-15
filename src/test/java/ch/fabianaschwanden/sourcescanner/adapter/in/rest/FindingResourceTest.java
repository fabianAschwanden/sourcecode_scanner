package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import ch.fabianaschwanden.sourcescanner.adapter.out.persistence.FindingRepository;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** REST + RBAC (WR-31) + Redaktion (WR-33) über @TestSecurity-Identitäten. */
@QuarkusTest
class FindingResourceTest {

    @Inject
    FindingRepository repository;

    private UUID seedFinding() {
        UUID id = UUID.randomUUID();
        repository.saveAll(List.of(new StoredFinding(id, UUID.randomUUID(), "repo-x",
                "secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH, "aws", "src/A.java", 1,
                "AKIA****MPLE", "fp-" + id, false, TriageStatus.OPEN, null, null, Instant.now(), Instant.now())));
        return id;
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_funde_lesen_aber_kein_scan_starten() {
        given().when().get("/api/findings").then().statusCode(200);
        given().contentType("application/json").body("{\"sourceId\":\"" + UUID.randomUUID() + "\",\"mode\":\"full\"}")
                .when().post("/api/scans").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "op", roles = "operator")
    void operator_triage_braucht_begruendung_fuer_false_positive() {
        UUID id = seedFinding();
        // ohne reason -> 400
        given().contentType("application/json").body("{\"status\":\"FALSE_POSITIVE\"}")
                .when().post("/api/findings/" + id + "/triage").then().statusCode(400);
        // mit reason -> 200
        given().contentType("application/json").body("{\"status\":\"FALSE_POSITIVE\",\"reason\":\"test\"}")
                .when().post("/api/findings/" + id + "/triage").then().statusCode(200)
                .body("triageStatus", equalTo("FALSE_POSITIVE"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void finding_dto_traegt_keinen_klartext() {
        UUID id = seedFinding();
        given().when().get("/api/findings/" + id).then().statusCode(200)
                .body("redactedMatch", not(equalTo("AKIAIOSFODNN7EXAMPLE")));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_nicht_bulk_triagieren() {
        given().contentType("application/json").body("{\"ids\":[],\"status\":\"BASELINE\"}")
                .when().post("/api/findings/bulk/triage").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "op", roles = "operator")
    void operator_bulk_triagiert_mehrere_funde() {
        UUID id1 = seedFinding();
        UUID id2 = seedFinding();
        given().contentType("application/json")
                .body("{\"ids\":[\"%s\",\"%s\"],\"status\":\"BASELINE\"}".formatted(id1, id2))
                .when().post("/api/findings/bulk/triage").then().statusCode(200)
                .body("total", equalTo(2))
                .body("succeeded", equalTo(2));
    }
}
