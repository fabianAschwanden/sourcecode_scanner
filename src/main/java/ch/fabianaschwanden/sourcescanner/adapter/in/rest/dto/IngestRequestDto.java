package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.CiMetadata;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.in.IngestScanResultUseCase.IngestRequest;
import ch.fabianaschwanden.sourcescanner.domain.port.in.IngestScanResultUseCase.IngestedFinding;
import java.util.List;
import java.util.Locale;

/**
 * Einlieferungs-Payload eines CI/CD-Laufs (IR-22). Funde sind <b>redigiert</b> (FR-18); der Server
 * speichert ausschliesslich den redigierten Treffer. CI-Metadaten ermöglichen Herkunft + Idempotenz.
 */
public record IngestRequestDto(
        String repoId,
        String mode,
        String status,
        String runRef,
        String pipelineUrl,
        String commit,
        String branch,
        String actor,
        List<FindingDtoIn> findings) {

    /** Einzulieferndes Finding (redigiert). */
    public record FindingDtoIn(String detectorId, String category, String severity, String ruleId,
                               String file, int line, String redactedMatch, String fingerprint,
                               boolean verified) {
    }

    public IngestRequest toDomain() {
        CiMetadata ci = new CiMetadata(runRef, pipelineUrl, commit, branch, actor);
        List<IngestedFinding> mapped = (findings == null ? List.<FindingDtoIn>of() : findings).stream()
                .map(f -> new IngestedFinding(f.detectorId(), parseCategory(f.category()),
                        parseSeverity(f.severity()), f.ruleId(), f.file(), f.line(), f.redactedMatch(),
                        f.fingerprint(), f.verified()))
                .toList();
        return new IngestRequest(repoId, mode, parseStatus(status), mapped, ci);
    }

    private DetectorCategory parseCategory(String s) {
        try {
            return s == null ? DetectorCategory.SECRET : DetectorCategory.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DetectorCategory.SECRET;
        }
    }

    private Severity parseSeverity(String s) {
        try {
            return s == null ? Severity.MEDIUM : Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    private ScanStatus parseStatus(String s) {
        try {
            return s == null ? ScanStatus.COMPLETED : ScanStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ScanStatus.COMPLETED;
        }
    }
}
