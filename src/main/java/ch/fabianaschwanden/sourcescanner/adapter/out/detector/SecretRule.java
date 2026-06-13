package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.regex.Pattern;

/** Eine kompilierte Regel des Gitleaks-Regelsatzes (id + Pattern + Default-Severity). */
public record SecretRule(String id, String description, Pattern pattern, Severity severity) {

    public SecretRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null for rule " + id);
        }
        severity = severity == null ? Severity.MEDIUM : severity;
    }
}
