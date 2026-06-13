package ch.fabianaschwanden.sourcescanner.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Roher Detektor-Fund (pre-Aggregation, docs/02 §3). Die Aggregationsschicht reichert ihn
 * später um {@code id}, {@code firstSeen} und {@code lastSeen} an (Phase 2).
 *
 * <p><b>Redaktion (FR-18):</b> {@code redactedMatch} enthält niemals den Klartext-Treffer.
 * Die Maskierung erfolgt im Detektor, bevor das Finding konstruiert wird.
 */
public record Finding(
        String detectorId,
        DetectorCategory category,
        Severity severity,
        String ruleId,
        String file,
        int line,
        String redactedMatch,
        String commitId,
        boolean verified) {

    public Finding {
        if (detectorId == null || detectorId.isBlank()) {
            throw new IllegalArgumentException("detectorId must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("file must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (redactedMatch == null) {
            throw new IllegalArgumentException("redactedMatch must not be null");
        }
    }

    /**
     * Deterministischer Fingerprint aus Regel-ID, Pfad und redigiertem Treffer (DR-41) —
     * Grundlage für SARIF-{@code partialFingerprints} und (ab Phase 2) Baseline/Dedup.
     * Arbeitet nur mit redigierten Daten, nie mit Klartext.
     */
    public String fingerprint() {
        String basis = ruleId + ':' + file + ':' + line + ':' + redactedMatch;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(basis.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
