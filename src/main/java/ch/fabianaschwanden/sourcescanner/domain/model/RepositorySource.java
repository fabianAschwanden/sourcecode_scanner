package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Eine im Server verwaltete Repository-Quelle (WR-02). Credentials werden ausschliesslich als
 * Secret-Store-Referenz gehalten ({@code tokenRef}, z. B. {@code env:NAME}), nie im Klartext (WR-32).
 * {@code reportEmails} sind optionale Empfänger, an die nach einem Scan dieses Repos ein Report geht
 * (WR-08, IR-53; opt-in über das Vorhandensein von Adressen). {@code remediationEnabled} ist das
 * Pro-Repo-Opt-in für Auto-Fix/Scrub — standardmässig {@code false} (RMR-02).
 */
public record RepositorySource(
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

    public RepositorySource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("source name must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("source type must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("source location must not be blank");
        }
        branches = branches == null ? List.of() : List.copyOf(branches);
        reportEmails = reportEmails == null ? List.of() : List.copyOf(reportEmails);
        description = description == null ? "" : description;
        visibility = visibility == null || visibility.isBlank() ? "private" : visibility;
    }

    /** Bequemer Konstruktor ohne Report-E-Mails / Remediation-Flag (Abwärtskompatibilität / Tests). */
    public RepositorySource(UUID id, String name, String type, String location,
                            List<String> branches, String tokenRef, boolean enabled) {
        this(id, name, type, location, branches, tokenRef, enabled, List.of(), false, "", "private");
    }

    /** Bequemer Konstruktor ohne Remediation-Flag (Abwärtskompatibilität / Tests). */
    public RepositorySource(UUID id, String name, String type, String location,
                            List<String> branches, String tokenRef, boolean enabled,
                            List<String> reportEmails) {
        this(id, name, type, location, branches, tokenRef, enabled, reportEmails, false, "", "private");
    }

    /** Bequemer Konstruktor ohne Beschreibung/Sichtbarkeit (Abwärtskompatibilität / Tests). */
    public RepositorySource(UUID id, String name, String type, String location,
                            List<String> branches, String tokenRef, boolean enabled,
                            List<String> reportEmails, boolean remediationEnabled) {
        this(id, name, type, location, branches, tokenRef, enabled, reportEmails, remediationEnabled, "", "private");
    }

    /** Wandelt die Quelle in eine scanbare {@link RepositoryRef} (für den Orchestrator). */
    public RepositoryRef toRef() {
        return new RepositoryRef(name, type, location, branches, tokenRef);
    }
}
