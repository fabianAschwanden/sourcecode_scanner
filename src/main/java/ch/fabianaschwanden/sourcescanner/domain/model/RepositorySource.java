package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Eine im Server verwaltete Repository-Quelle (WR-02). Credentials werden ausschliesslich als
 * Secret-Store-Referenz gehalten ({@code tokenRef}, z. B. {@code env:NAME}), nie im Klartext (WR-32).
 * {@code reportEmails} sind optionale Empfänger, an die nach einem Scan dieses Repos ein Report geht
 * (WR-08, IR-53; opt-in über das Vorhandensein von Adressen).
 */
public record RepositorySource(
        UUID id,
        String name,
        String type,
        String location,
        List<String> branches,
        String tokenRef,
        boolean enabled,
        List<String> reportEmails) {

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
    }

    /** Bequemer Konstruktor ohne Report-E-Mails (Abwärtskompatibilität / Tests). */
    public RepositorySource(UUID id, String name, String type, String location,
                            List<String> branches, String tokenRef, boolean enabled) {
        this(id, name, type, location, branches, tokenRef, enabled, List.of());
    }

    /** Wandelt die Quelle in eine scanbare {@link RepositoryRef} (für den Orchestrator). */
    public RepositoryRef toRef() {
        return new RepositoryRef(name, type, location, branches, tokenRef);
    }
}
