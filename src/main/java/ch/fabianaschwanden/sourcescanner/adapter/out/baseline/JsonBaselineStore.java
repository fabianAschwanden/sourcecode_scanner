package ch.fabianaschwanden.sourcescanner.adapter.out.baseline;

import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.BaselineEntry;
import ch.fabianaschwanden.sourcescanner.domain.port.out.BaselinePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Liest/schreibt die Baseline als JSON ({@code .scanner-baseline.json}, docs/03 §5-Format). */
@ApplicationScoped
public class JsonBaselineStore implements BaselinePort {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Optional<Baseline> load(Path baselineFile) {
        if (baselineFile == null || !Files.isRegularFile(baselineFile)) {
            return Optional.empty();
        }
        try {
            JsonNode root = json.readTree(Files.readAllBytes(baselineFile));
            int version = root.path("version").asInt(1);
            Instant generatedAt = parseInstant(root.path("generatedAt").asText(null));
            List<BaselineEntry> entries = new ArrayList<>();
            JsonNode arr = root.get("entries");
            if (arr != null && arr.isArray()) {
                for (JsonNode e : arr) {
                    entries.add(new BaselineEntry(
                            e.path("fingerprint").asText(),
                            text(e, "acceptedBy"),
                            text(e, "acceptedAt"),
                            text(e, "reason")));
                }
            }
            return Optional.of(new Baseline(version, generatedAt, entries));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read baseline " + baselineFile, e);
        }
    }

    @Override
    public void write(Baseline baseline, Path baselineFile) {
        ObjectNode root = json.createObjectNode();
        root.put("version", baseline.version());
        root.put("generatedAt", baseline.generatedAt().toString());
        ArrayNode entries = root.putArray("entries");
        for (BaselineEntry entry : baseline.entries()) {
            ObjectNode e = entries.addObject();
            e.put("fingerprint", entry.fingerprint());
            putIfPresent(e, "acceptedBy", entry.acceptedBy());
            putIfPresent(e, "acceptedAt", entry.acceptedAt());
            putIfPresent(e, "reason", entry.reason());
        }
        try {
            Path parent = baselineFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            json.writerWithDefaultPrettyPrinter().writeValue(baselineFile.toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write baseline " + baselineFile, e);
        }
    }

    private static Instant parseInstant(String raw) {
        try {
            return raw == null ? Instant.now() : Instant.parse(raw);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }
}
