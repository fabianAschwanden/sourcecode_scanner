package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import java.util.List;
import java.util.UUID;

/**
 * REST-Transport einer Repository-Quelle. {@code tokenRef} ist nur die Secret-Referenz; ein
 * Klartext-Token wird nie zurückgegeben (WR-32). {@code reportEmails} sind die optionalen
 * Report-Empfänger je Repo (WR-08, IR-53).
 */
public record RepositorySourceDto(
        UUID id,
        String name,
        String type,
        String location,
        List<String> branches,
        String tokenRef,
        boolean enabled,
        List<String> reportEmails,
        boolean remediationEnabled,
        String description,
        String visibility) {

    public RepositorySourceDto {
        branches = branches == null ? List.of() : branches;
        reportEmails = reportEmails == null ? List.of() : reportEmails;
    }

    public static RepositorySourceDto from(RepositorySource s) {
        return new RepositorySourceDto(s.id(), s.name(), s.type(), s.location(), s.branches(),
                s.tokenRef(), s.enabled(), s.reportEmails(), s.remediationEnabled(),
                s.description(), s.visibility());
    }

    public RepositorySource toDomain() {
        return new RepositorySource(id, name, type, location, branches, tokenRef, enabled,
                reportEmails, remediationEnabled, description, visibility);
    }
}
