package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.VerificationResult;
import org.junit.jupiter.api.Test;

class SeverityScoringTest {

    private Finding finding(Severity severity) {
        return new Finding("d", DetectorCategory.SECRET, severity, "aws", "f.txt", 1, "***", "c", false);
    }

    @Test
    void aktives_secret_wird_auf_critical_hochgestuft_und_verified() {
        Finding result = SeverityScoring.escalateIfActive(finding(Severity.HIGH),
                VerificationResult.active("ok"));
        assertEquals(Severity.CRITICAL, result.severity());
        assertTrue(result.verified());
    }

    @Test
    void unverified_laesst_fund_unveraendert() {
        Finding original = finding(Severity.MEDIUM);
        assertSame(original, SeverityScoring.escalateIfActive(original, VerificationResult.unverified()));
    }

    @Test
    void inaktiv_aendert_severity_nicht() {
        Finding original = finding(Severity.HIGH);
        Finding result = SeverityScoring.escalateIfActive(original, VerificationResult.inactive("revoked"));
        assertEquals(Severity.HIGH, result.severity());
        assertFalse(result.verified());
    }
}
