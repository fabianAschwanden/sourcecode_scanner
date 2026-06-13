package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;

import ch.fabianaschwanden.sourcescanner.adapter.out.persistence.FindingRepository;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * REST + RBAC der Remediation-Endpunkte (RMR-40/41) und die Gate-Sperren als HTTP-Status:
 * Viewer 403; deaktiviertes Repo/Opt-out ⇒ 409; Scrub-Execute nur Admin; Dry-Run vor Execute Pflicht.
 */
@QuarkusTest
class RemediationResourceTest {

    @Inject
    FindingRepository findings;

    @Inject
    RepositorySourcePort sources;

    private UUID seedFinding(String repoName) {
        UUID id = UUID.randomUUID();
        findings.saveAll(List.of(new StoredFinding(id, UUID.randomUUID(), repoName,
                "secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH, "aws", "src/A.java", 1,
                "AKIA****MPLE", "fp-" + id, false, TriageStatus.OPEN, null, null, Instant.now(), Instant.now())));
        return id;
    }

    private RepositorySource seedSource(String name, boolean remediationEnabled) {
        return sources.save(new RepositorySource(null, name, "github",
                "https://github.com/o/" + name + ".git", List.of("main"), null, true,
                List.of(), remediationEnabled));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_nicht_remediieren_oder_scrubben() {
        UUID id = seedFinding("repo-viewer");
        given().contentType("application/json").when()
                .post("/api/findings/" + id + "/remediate").then().statusCode(403);
        given().contentType("application/json").when()
                .post("/api/repos/" + UUID.randomUUID() + "/scrub/dry-run").then().statusCode(403);
        given().contentType("application/json").body("{}").when()
                .post("/api/repos/" + UUID.randomUUID() + "/scrub/execute").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "op-user", roles = "operator")
    void operator_remediation_blockiert_wenn_repo_opt_out() {
        RepositorySource src = seedSource("repo-optout-" + UUID.randomUUID(), false);
        UUID id = seedFinding(src.name());
        given().contentType("application/json").when()
                .post("/api/findings/" + id + "/remediate").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "op-user", roles = "operator")
    void operator_darf_kein_scrub_execute() {
        RepositorySource src = seedSource("repo-exec-" + UUID.randomUUID(), true);
        given().contentType("application/json").body("{\"forcePushApproved\":true,\"rotationConfirmed\":true}")
                .when().post("/api/repos/" + src.id() + "/scrub/execute").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_scrub_execute_ohne_dry_run_blockiert() {
        RepositorySource src = seedSource("repo-nodry-" + UUID.randomUUID(), true);
        // Ohne vorausgegangenen Dry-Run ⇒ Gate DRY_RUN blockiert ⇒ 409.
        given().contentType("application/json").body("{\"forcePushApproved\":true,\"rotationConfirmed\":true}")
                .when().post("/api/repos/" + src.id() + "/scrub/execute").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_dry_run_dann_execute_meldet_werkzeug_fehlt() {
        RepositorySource src = seedSource("repo-flow-" + UUID.randomUUID(), true);
        seedFinding(src.name());
        // Dry-Run zuerst (Pflicht), liefert Bericht.
        given().contentType("application/json").when()
                .post("/api/repos/" + src.id() + "/scrub/dry-run").then().statusCode(200);
        // Execute mit Freigaben: Gates grün bis auf das nicht installierte Werkzeug ⇒ 409 (Gate TOOL).
        given().contentType("application/json").body("{\"forcePushApproved\":true,\"rotationConfirmed\":true}")
                .when().post("/api/repos/" + src.id() + "/scrub/execute").then().statusCode(409);
    }
}
