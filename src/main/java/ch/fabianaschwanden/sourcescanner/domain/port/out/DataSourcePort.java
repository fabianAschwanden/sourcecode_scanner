package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import java.util.List;
import java.util.Map;

/**
 * Zugang zu einer externen REST-Datenquelle (IR-60). Kapselt Aufruf, Auth (nur Secret-Referenz) und
 * JSONPath-Extraktion. Die Domäne kennt nur diesen Port; der REST-Adapter liegt dahinter.
 *
 * <p><b>Datenschutz:</b> {@link #loadValues} liefert vertrauliche Klartextwerte ausschliesslich
 * flüchtig im Speicher an den Detektor; sie werden nie geloggt oder persistiert (NFR-23, IR-64).
 * {@link #probe} gibt nur ein <b>redigiertes</b> Schema zurück (IR-63).
 */
public interface DataSourcePort {

    /** Erreichbarkeit prüfen und ein redigiertes Attribut-Schema für das UI-Mapping liefern (IR-63). */
    DataSourceSchema probe(DataSourceDefinition definition);

    /**
     * Lädt je geprüftem Attribut die distinct Werte (für den Wertabgleich im Detektor, DR-23/24).
     * Rückgabe nur im Speicher; Aufrufer behandelt sie als Geheimnis (FR-23).
     */
    Map<AttributeRule, List<String>> loadValues(DataSourceDefinition definition);
}
