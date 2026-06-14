package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.KeyValuePair;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verarbeitet eine hochgeladene Key-Value-Liste für eine UPLOAD-Datenquelle (IR-67, WR-56). Die Werte
 * werden gehasht persistiert (NFR-23); je vorkommendem Key wird automatisch eine Attribut-Regel
 * angelegt (falls noch nicht vorhanden). Pflege nur Operator/Admin (NFR-24); Audit (NFR-11).
 */
public interface UploadKeyValuesUseCase {

    /**
     * Speichert die Hashes der übergebenen Paare für die Datenquelle und meldet je Attribut die Anzahl
     * gespeicherter Hashes. Der Klartext verlässt diese Methode nicht.
     */
    Map<String, Integer> upload(UUID dataSourceId, List<KeyValuePair> pairs, String actor);
}
