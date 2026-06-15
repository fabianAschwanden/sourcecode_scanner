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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Repo-Übersicht (WR-80..84): Karten-Endpoint mit serverseitiger Suche/Filter und abgeleiteter Sprache
 * (dominanter Dateityp der Funde). RBAC: Lesen ab Viewer.
 */
@QuarkusTest
class RepositoryCardsResourceTest {

    @Inject
    RepositorySourcePort sources;

    @Inject
    FindingRepository findings;

    private RepositorySource seedSource(String name, String type, String description) {
        return sources.save(new RepositorySource(null, name, type, "loc/" + name, List.of("main"), null,
                true, List.of(), false, description, "public"));
    }

    private void seedJavaFinding(String repoName) {
        findings.saveAll(List.of(new StoredFinding(UUID.randomUUID(), UUID.randomUUID(), repoName,
                "secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH, "aws", "src/A.java", 1,
                "AKIA****MPLE", "fp-" + UUID.randomUUID(), false, TriageStatus.OPEN, null, null,
                Instant.now(), Instant.now())));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void karte_traegt_abgeleitete_sprache_aus_funden() {
        String name = "cards-java-" + UUID.randomUUID();
        seedSource(name, "github", "demo repo");
        seedJavaFinding(name);

        given().when().get("/api/sources/cards?q=" + name).then().statusCode(200)
                .body("find { it.name == '" + name + "' }.language", Matchers.equalTo("java"))
                .body("find { it.name == '" + name + "' }.visibility", Matchers.equalTo("public"))
                .body("find { it.name == '" + name + "' }.description", Matchers.equalTo("demo repo"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void typ_filter_grenzt_ein() {
        String gh = "cards-gh-" + UUID.randomUUID();
        String local = "cards-local-" + UUID.randomUUID();
        seedSource(gh, "github", "");
        seedSource(local, "localGit", "");

        given().when().get("/api/sources/cards?type=localGit").then().statusCode(200)
                .body("name", Matchers.hasItem(local))
                .body("name", Matchers.not(Matchers.hasItem(gh)));
    }
}
