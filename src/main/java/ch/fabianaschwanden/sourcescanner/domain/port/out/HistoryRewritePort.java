package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScrubDryRun;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubResult;

/**
 * Kapselt das eigentliche History-Rewrite (RMR-20/21/28) hinter der Domäne. Die Implementierung
 * arbeitet auf einem frischen Mirror-Klon (RMR-21) und ruft ein externes Werkzeug (git-filter-repo,
 * alternativ BFG, RMR-28) auf. Ist das Werkzeug nicht installiert, liefert {@link #available()}
 * {@code false}; {@link #dryRun} bleibt nutzbar (geplanter Bericht), {@link #execute} verweigert.
 */
public interface HistoryRewritePort {

    /** {@code true}, wenn das reale Rewrite-Werkzeug vorhanden und ein scharfer Lauf möglich ist. */
    boolean available();

    /** Pflicht-Vorschau vor jedem realen Lauf (RMR-22): zeigt redigiert, was entfernt würde. */
    ScrubDryRun dryRun(ScrubRequest request);

    /** Führt das Rewrite aus (Mirror-Klon → Filter → Re-Scan). Verweigert, wenn nicht verfügbar. */
    ScrubResult execute(ScrubRequest request);
}
