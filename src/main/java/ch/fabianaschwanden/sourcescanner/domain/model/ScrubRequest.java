package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Auftrag zur History-Bereinigung eines Repos (RMR-20/21). Es wird stets ein <b>frischer Mirror-Klon</b>
 * bearbeitet (RMR-21); {@code tokenRef} ist nur eine Referenz (env:/vault:). {@code replacements}
 * benennen die zu entfernenden Secrets (redigiert, ohne Klartext, RMR-12).
 */
public record ScrubRequest(
        String repoUrl,
        String tokenRef,
        List<ScrubReplacement> replacements,
        boolean forcePushApproved) {

    public ScrubRequest {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
    }
}
