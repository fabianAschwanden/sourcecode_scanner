package ch.fabianaschwanden.sourcescanner.adapter.out.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimaler JSONPath-Auszug für Datensatz-Selektoren (IR-62) — bewusst ohne externe Bibliothek.
 * Unterstützt: {@code $} (Wurzel), {@code $[*]} (Wurzel-Array), gepunktete Felder ({@code $.data},
 * {@code $.result.items}) und ein abschliessendes {@code [*]} zum Aufspannen eines Arrays. Reicht für
 * die dokumentierten Muster ({@code $.data[*]}, {@code $[*]}); unbekannte Ausdrücke ⇒ leere Liste.
 */
final class JsonRecords {

    private JsonRecords() {
    }

    /** Wählt die Datensatz-Knoten aus dem Antwort-Baum gemäss {@code recordsPath}. */
    static List<JsonNode> select(JsonNode root, String recordsPath) {
        if (root == null || root.isMissingNode()) {
            return List.of();
        }
        String expr = recordsPath == null || recordsPath.isBlank() ? "$[*]" : recordsPath.trim();
        if (expr.startsWith("$")) {
            expr = expr.substring(1);
        }
        boolean expand = expr.endsWith("[*]");
        if (expand) {
            expr = expr.substring(0, expr.length() - 3);
        }
        JsonNode node = root;
        for (String segment : expr.split("\\.")) {
            String name = segment.trim();
            if (name.isEmpty()) {
                continue;
            }
            node = node.path(name);
        }
        List<JsonNode> records = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(records::add);
        } else if (!node.isMissingNode() && !node.isNull()) {
            records.add(node);
        }
        return records;
    }
}
