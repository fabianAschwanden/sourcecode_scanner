package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.domain.model.KeyValuePair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Parst den Rohinhalt eines Key-Value-Uploads (IR-67) und erkennt das Format automatisch: JSON, wenn
 * der Inhalt mit {@code [} oder &#123; beginnt, sonst CSV. JSON-Formen: Array von {@code {key,value}},
 * Array von {@code {key:value}}-Objekten oder ein einzelnes Objekt. CSV: zwei Spalten {@code key,value}
 * (optionale Kopfzeile), Trenner Komma oder Semikolon. Liegt in der REST-Schicht (Eingabeformat des
 * Upload-Endpoints); der Klartext lebt nur flüchtig — der Aufrufer hasht und verwirft ihn (NFR-23).
 */
final class KeyValueUploadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KeyValueUploadParser() {
    }

    static List<KeyValuePair> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String trimmed = content.stripLeading();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }
        return parseCsv(content);
    }

    private static List<KeyValuePair> parseJson(String content) {
        List<KeyValuePair> pairs = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has("key") && node.has("value")) {
                        addPair(pairs, node.get("key").asText(), node.get("value").asText());
                    } else if (node.isObject()) {
                        node.properties().forEach(e -> addPair(pairs, e.getKey(), textOf(e.getValue())));
                    }
                }
            } else if (root.isObject()) {
                root.properties().forEach(e -> addPair(pairs, e.getKey(), textOf(e.getValue())));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON key-value upload: " + e.getMessage(), e);
        }
        return pairs;
    }

    private static List<KeyValuePair> parseCsv(String content) {
        List<KeyValuePair> pairs = new ArrayList<>();
        boolean first = true;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split("[,;]", 2);
            if (cols.length < 2) {
                continue;
            }
            String key = unquote(cols[0].trim());
            String value = unquote(cols[1].trim());
            if (first && key.equalsIgnoreCase("key") && value.equalsIgnoreCase("value")) {
                first = false;
                continue;
            }
            first = false;
            addPair(pairs, key, value);
        }
        return pairs;
    }

    private static void addPair(List<KeyValuePair> pairs, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        pairs.add(new KeyValuePair(key.trim(), value));
    }

    private static String textOf(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
