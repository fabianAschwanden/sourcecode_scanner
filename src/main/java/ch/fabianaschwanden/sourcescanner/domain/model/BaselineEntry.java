package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ein akzeptierter Altfund in der Baseline (docs/03 §5). Identifiziert über den
 * deterministischen {@code fingerprint} eines {@link Finding}.
 */
public record BaselineEntry(String fingerprint, String acceptedBy, String acceptedAt, String reason) {

    public BaselineEntry {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("baseline entry fingerprint must not be blank");
        }
    }
}
