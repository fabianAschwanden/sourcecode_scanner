package ch.fabianaschwanden.sourcescanner.domain.port.out;

/**
 * Prüft, ob eine Credential-/Secret-Referenz (z. B. {@code env:GITHUB_TOKEN}) aktuell auflösbar ist
 * (WR-17). Liefert nur den Status, niemals den Klartext-Wert (WR-32).
 */
public interface SecretReferencePort {

    boolean resolvable(String reference);
}
