package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.VerificationResult;

/**
 * Severity-Modifikation durch Kontext (docs/01 §3.4). Pure Domänen-Logik, framework-frei.
 * Phase 3: ein verifiziert aktives Secret wird auf CRITICAL hochgestuft und als {@code verified}
 * markiert (DR-14).
 */
public final class SeverityScoring {

    private SeverityScoring() {
    }

    /**
     * Liefert den (ggf. hochgestuften) Fund nach Verifikation. Bei {@link VerificationResult#isActive()}
     * → CRITICAL und {@code verified=true}; sonst unverändert (immutable: neue Instanz nur bei Änderung).
     */
    public static Finding escalateIfActive(Finding finding, VerificationResult verification) {
        if (verification == null || !verification.isActive()) {
            return finding;
        }
        if (finding.severity() == Severity.CRITICAL && finding.verified()) {
            return finding;
        }
        return new Finding(
                finding.detectorId(),
                finding.category(),
                Severity.CRITICAL,
                finding.ruleId(),
                finding.file(),
                finding.line(),
                finding.redactedMatch(),
                finding.commitId(),
                true);
    }
}
