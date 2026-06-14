package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.CiMetadata;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;

/**
 * Driving Port — nimmt das Ergebnis eines CI/CD-Laufs entgegen und legt es in der zentralen DB ab
 * (IR-22/24/25). Funde sind bereits redigiert (FR-18); die Einlieferung ist über {@code ci.runRef}
 * idempotent. RBAC: nur die Service-Rolle {@code ci} (IR-23).
 */
public interface IngestScanResultUseCase {

    /** Legt den Lauf + seine redigierten Funde ab und liefert den persistierten {@link ScanRecord}. */
    ScanRecord ingest(IngestRequest request, String actor);

    /** Ein einzulieferndes (redigiertes) Finding. */
    record IngestedFinding(String detectorId, DetectorCategory category, Severity severity, String ruleId,
                           String file, int line, String redactedMatch, String fingerprint, boolean verified) {
        public IngestedFinding {
            if (ruleId == null || ruleId.isBlank()) {
                throw new IllegalArgumentException("ruleId must not be blank");
            }
            if (redactedMatch == null) {
                throw new IllegalArgumentException("redactedMatch must not be null");
            }
            category = category == null ? DetectorCategory.SECRET : category;
            severity = severity == null ? Severity.MEDIUM : severity;
        }
    }

    /** Einlieferungs-Auftrag eines abgeschlossenen CI-Laufs. */
    record IngestRequest(String repoId, String mode, ScanStatus status, List<IngestedFinding> findings,
                         CiMetadata ci) {
        public IngestRequest {
            if (repoId == null || repoId.isBlank()) {
                throw new IllegalArgumentException("repoId must not be blank");
            }
            mode = mode == null || mode.isBlank() ? "diff" : mode;
            status = status == null ? ScanStatus.COMPLETED : status;
            findings = findings == null ? List.of() : List.copyOf(findings);
            ci = ci == null ? CiMetadata.NONE : ci;
        }
    }
}
