package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.RuleMatchMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleOverride;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.Locale;

/** REST-Transport einer Regel-Einstellung (DR-50..52). {@code severity} optional (null = Default). */
public record RuleOverrideDto(
        String ruleId,
        boolean enabled,
        String severity,
        String matchMode,
        String dataSourceName) {

    public static RuleOverrideDto from(RuleOverride o) {
        return new RuleOverrideDto(o.ruleId(), o.enabled(),
                o.severity() == null ? null : o.severity().name(), o.matchMode().name(), o.dataSourceName());
    }

    public RuleOverride toDomain() {
        return new RuleOverride(ruleId, enabled, parseSeverity(severity), parseMode(matchMode), dataSourceName);
    }

    private Severity parseSeverity(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RuleMatchMode parseMode(String s) {
        try {
            return s == null ? RuleMatchMode.ALWAYS : RuleMatchMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RuleMatchMode.ALWAYS;
        }
    }
}
