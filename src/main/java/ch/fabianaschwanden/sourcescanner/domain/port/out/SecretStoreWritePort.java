package ch.fabianaschwanden.sourcescanner.domain.port.out;

/**
 * Schreibt einen Klartext-Wert in einen externen Secret-Store (Vault) und liefert die entstehende
 * Referenz (IR-30, Modus VAULT_WRITE). Der Klartext verlässt diese Methode nur Richtung Store; der
 * Aufrufer verwirft ihn danach. Ohne angebundenen Store ist der Modus nicht verfügbar.
 */
public interface SecretStoreWritePort {

    boolean available();

    /** Schreibt {@code plaintext} an {@code path} und gibt die Referenz zurück (z. B. {@code vault:path#key}). */
    String write(String path, String plaintext);
}
