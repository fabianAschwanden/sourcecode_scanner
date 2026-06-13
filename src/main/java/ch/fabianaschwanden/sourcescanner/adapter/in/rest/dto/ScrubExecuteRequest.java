package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

/**
 * Anforderung eines realen Scrub-Laufs (RMR-25/26): {@code forcePushApproved} ist die separate
 * Force-Push-Freigabe (Admin/Break-Glass), {@code rotationConfirmed} bestätigt die vorausgegangene
 * Rotation aktiver Secrets (Rotation-Gate). Beide standardmässig {@code false}.
 */
public record ScrubExecuteRequest(boolean forcePushApproved, boolean rotationConfirmed) {
}
