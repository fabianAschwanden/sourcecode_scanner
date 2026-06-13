package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.ScrubDryRun;

/** REST-Transport eines Scrub-Dry-Run-Berichts (RMR-22). Redigiert (RMR-12). */
public record ScrubDryRunDto(boolean toolAvailable, int affectedSecrets, String diffSummary) {

    public static ScrubDryRunDto from(ScrubDryRun d) {
        return new ScrubDryRunDto(d.toolAvailable(), d.affectedSecrets(), d.diffSummary());
    }
}
