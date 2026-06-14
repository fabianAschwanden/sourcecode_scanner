package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistenz der UI-verwalteten Secrets (WR-17/19). Speichert je nach Modus eine Referenz oder einen
 * <b>bereits verschlüsselten</b> Wert (NFR-29) — nie Klartext. Liefert nach aussen nur das redigierte
 * {@link ManagedSecret} (ohne Wert).
 */
public interface ManagedSecretPort {

    /** Speichert einen Eintrag; {@code encryptedValue} nur bei Modus DB_ENCRYPTED (sonst {@code null}). */
    ManagedSecret save(UUID id, String name, SecretStorageMode mode, String reference, String encryptedValue);

    List<ManagedSecret> all();

    Optional<ManagedSecret> byId(UUID id);

    Optional<ManagedSecret> byName(String name);

    /** Der gespeicherte (verschlüsselte) Wert eines DB_ENCRYPTED-Secrets — nur intern zum Auflösen. */
    Optional<String> encryptedValue(UUID id);

    void delete(UUID id);
}
