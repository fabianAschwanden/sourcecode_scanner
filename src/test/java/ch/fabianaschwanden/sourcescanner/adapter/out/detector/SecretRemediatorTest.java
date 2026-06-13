package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStrategy;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Konservative, redigierte Fix-Vorschläge für Secret-Funde (RMR-13/14/12) — direkt testbar. */
class SecretRemediatorTest {

    private final SecretRemediator remediator = new SecretRemediator();

    private Finding secret() {
        return new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH, "aws",
                "src/A.java", 2, "AKIA****MPLE", "c1", false);
    }

    private ScanUnit unit() {
        return new ScanUnit("repo", "src/A.java", "c1", "me", Instant.now(),
                "line1\nString k = \"AKIASECRET\";\nline3", null);
    }

    @Test
    void schlaegt_annotate_fuer_secret_vor() {
        Optional<RemediationProposal> p = remediator.propose(secret(), unit());
        assertTrue(p.isPresent());
        assertEquals(RemediationStrategy.ANNOTATE, p.get().strategy());
        // Edit hängt den Suppress-Kommentar an die bestehende Zeile (die Datei bleibt lauffähig);
        // die menschliche Zusammenfassung (für PR-Bodies) bleibt redigiert, kein Klartext (RMR-12).
        String content = p.get().edits().getFirst().newContent();
        assertTrue(content.contains("scanner:ignore-secret"));
        assertFalse(p.get().humanSummary().contains("AKIASECRET"),
                "die PR-taugliche Zusammenfassung darf keinen Klartext tragen");
    }

    @Test
    void ignoriert_nicht_secret_funde() {
        Finding notSecret = new Finding("pii.regex", DetectorCategory.PII, Severity.HIGH, "email",
                "src/A.java", 1, "x", "c1", false);
        assertTrue(remediator.propose(notSecret, unit()).isEmpty());
    }

    @Test
    void stored_pfad_nutzt_append_marker_ohne_klartext() {
        StoredFinding sf = new StoredFinding(UUID.randomUUID(), UUID.randomUUID(), "repo",
                "secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH, "aws", "src/A.java", 2,
                "AKIA****MPLE", "fp1", false, TriageStatus.OPEN, null, null, Instant.now(), Instant.now());
        Optional<RemediationProposal> p = remediator.proposeForStored(sf);
        assertTrue(p.isPresent());
        String content = p.get().edits().getFirst().newContent();
        assertTrue(content.startsWith(SecretRemediator.APPEND_MARKER));
        assertTrue(content.contains("scanner:ignore-secret"));
    }
}
