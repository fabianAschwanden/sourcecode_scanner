package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** REST-Transport einer Governance-Policy (FR-20). REST-DTOs leben nur im REST-Adapter (TR-23). */
public record PolicyDto(
        UUID id,
        String orgUnit,
        String failOn,
        boolean failOnNewOnly,
        boolean softFail,
        String warnThreshold,
        List<String> enabledDetectorGroups) {

    public static PolicyDto from(Policy p) {
        return new PolicyDto(p.id(), p.orgUnit(), p.gate().failOn().name(), p.gate().failOnNewOnly(),
                p.gate().softFail(), p.warnThreshold().name(),
                List.copyOf(p.enabledDetectorGroups()));
    }

    public Policy toDomain() {
        Severity failOnSev = Severity.valueOf(failOn.trim().toUpperCase(Locale.ROOT));
        Severity warn = warnThreshold == null || warnThreshold.isBlank()
                ? Severity.MEDIUM : Severity.valueOf(warnThreshold.trim().toUpperCase(Locale.ROOT));
        Set<String> groups = enabledDetectorGroups == null ? Set.of() : Set.copyOf(enabledDetectorGroups);
        return new Policy(id, orgUnit, new GateConfig(failOnSev, failOnNewOnly, softFail), groups, warn);
    }
}
