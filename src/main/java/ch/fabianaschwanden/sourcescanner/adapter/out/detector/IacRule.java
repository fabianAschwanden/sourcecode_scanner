package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.regex.Pattern;

/** Eine IaC-Regel: Muster + Severity + Ziel-Technologie (terraform|kubernetes|dockerfile). */
public record IacRule(String id, String description, Pattern pattern, Severity severity, String target) {

    public IacRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("iac rule id must not be blank");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null for rule " + id);
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank for rule " + id);
        }
        severity = severity == null ? Severity.MEDIUM : severity;
    }
}
