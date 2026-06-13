package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.ScrubDryRun;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubResult;
import java.util.UUID;

/**
 * Driving Port — History-Bereinigung eines Repos (RMR-20). Dry-Run ist vor {@code execute} zwingend
 * (RMR-22); {@code execute} verlangt zusätzlich Force-Push-Freigabe (RMR-25, Admin/Break-Glass) und
 * scheitert hart, wenn das Rotation-Gate verletzt ist (RMR-26). Opt-in pro Repo, global aus (RMR-02).
 */
public interface ScrubHistoryUseCase {

    /** Pflicht-Vorschau: berechnet/holt einen redigierten Bericht, ohne die Historie zu ändern (RMR-22). */
    ScrubDryRun dryRun(UUID repoId, String actor);

    /** Realer Lauf — nur nach erfolgtem Dry-Run + Force-Push-Freigabe und mit grünem Rotation-Gate. */
    ScrubResult execute(UUID repoId, boolean forcePushApproved, boolean rotationConfirmed, String actor);
}
