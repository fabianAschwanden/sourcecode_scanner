package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Darstellungsmodell einer Repo-Karte für die Übersicht (WR-82). Bündelt die verwaltete Quelle mit
 * <b>abgeleiteten</b> Anzeigewerten: {@code language} (dominanter Dateityp der letzten Funde) und
 * {@code lastScanAt} (Zeitpunkt des letzten Laufs). Lizenz/Sterne sind bewusst nicht enthalten.
 */
public record RepositoryCard(
        UUID id,
        String name,
        String type,
        String visibility,
        String description,
        boolean enabled,
        String language,
        Instant lastScanAt,
        String lastStatus,
        String lastError) {

    public static RepositoryCard from(RepositorySource source, String language, Instant lastScanAt,
                                      String lastStatus, String lastError) {
        return new RepositoryCard(source.id(), source.name(), source.type(), source.visibility(),
                source.description(), source.enabled(), language, lastScanAt, lastStatus, lastError);
    }
}
