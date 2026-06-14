package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Speichermodus eines über die UI verwalteten Secrets (WR-19):
 * <ul>
 *   <li>{@link #REFERENCE} — nur eine Secret-Store-Referenz ({@code env:}/{@code vault:}), kein Wert
 *       im Backend (Default, WR-32-konform).</li>
 *   <li>{@link #VAULT_WRITE} — Klartext wird an den Secret-Store geschrieben; behalten wird nur die
 *       entstehende Referenz, der Klartext wird sofort verworfen (IR-30).</li>
 *   <li>{@link #DB_ENCRYPTED} — Klartext at-rest <b>verschlüsselt</b> in der zentralen DB (NFR-29/30);
 *       bewusste, dokumentierte Abweichung von WR-32.</li>
 * </ul>
 */
public enum SecretStorageMode {
    REFERENCE,
    VAULT_WRITE,
    DB_ENCRYPTED
}
