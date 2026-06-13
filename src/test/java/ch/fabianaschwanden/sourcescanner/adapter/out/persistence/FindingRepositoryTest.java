package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Persistenz-Round-Trip gegen Dev-Services-Postgres; liefert Domänen-Modelle, nie Entities (TR-23). */
@QuarkusTest
class FindingRepositoryTest {

    @Inject
    FindingRepository repository;

    private StoredFinding finding(String repo, Severity sev, TriageStatus status) {
        return new StoredFinding(UUID.randomUUID(), UUID.randomUUID(), repo, "secret.regex-ruleset",
                DetectorCategory.SECRET, sev, "aws", "src/A.java", 1, "AKIA****MPLE",
                "fp-" + UUID.randomUUID(), false, status, null, null, Instant.now(), Instant.now());
    }

    @Test
    void speichert_und_filtert_nach_repo_und_severity() {
        String repo = "repo-" + UUID.randomUUID();
        repository.saveAll(List.of(
                finding(repo, Severity.HIGH, TriageStatus.OPEN),
                finding(repo, Severity.LOW, TriageStatus.OPEN)));

        List<StoredFinding> high = repository.query(
                new FindingPort.FindingQuery(repo, Severity.HIGH, null, null, 0, 100));

        assertEquals(1, high.size());
        assertEquals(Severity.HIGH, high.getFirst().severity());
        assertTrue(high.getFirst().redactedMatch().contains("*"));
    }

    @Test
    void triage_status_wird_persistiert() {
        String repo = "repo-" + UUID.randomUUID();
        StoredFinding f = finding(repo, Severity.HIGH, TriageStatus.OPEN);
        repository.saveAll(List.of(f));

        repository.save(f.withTriage(TriageStatus.FALSE_POSITIVE, "test fixture"));

        StoredFinding reloaded = repository.byId(f.id()).orElseThrow();
        assertEquals(TriageStatus.FALSE_POSITIVE, reloaded.triageStatus());
        assertEquals("test fixture", reloaded.triageReason());
    }
}
