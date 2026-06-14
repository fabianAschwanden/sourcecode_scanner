package ch.fabianaschwanden.sourcescanner.domain.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistenz der <b>Hashes</b> hochgeladener Key-Value-Werte je Datenquelle (IR-67, NFR-23). Es wird
 * niemals ein Klartextwert gespeichert oder zurückgegeben — nur die Hashes, gegen die der Detektor
 * Code-Tokens prüft. Der Schlüssel ist der Attributname (z. B. {@code partnernummer}).
 */
public interface DataSourceValuePort {

    /** Ersetzt die gespeicherten Hashes einer Datenquelle durch die übergebenen (je Attribut). */
    void replace(UUID dataSourceId, Map<String, Set<String>> hashesByAttribute);

    /** Lädt die gespeicherten Hashes je Attribut (für den Detektor-Abgleich). */
    Map<String, Set<String>> hashesByAttribute(UUID dataSourceId);

    /** Anzahl gespeicherter Hashes je Attribut (für Statistik in der UI; nie Werte). */
    Map<String, Integer> countByAttribute(UUID dataSourceId);

    /** Entfernt alle Hashes einer Datenquelle (z. B. beim Löschen der Quelle). */
    void deleteFor(UUID dataSourceId);
}
