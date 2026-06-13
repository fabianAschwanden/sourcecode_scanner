package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.ScrubResult;

/** REST-Transport eines (versuchten) Scrub-Laufs (RMR-24). Redigiert (RMR-12). */
public record ScrubResultDto(boolean success, int remainingFindings, String message) {

    public static ScrubResultDto from(ScrubResult r) {
        return new ScrubResultDto(r.success(), r.remainingFindings(), r.message());
    }
}
