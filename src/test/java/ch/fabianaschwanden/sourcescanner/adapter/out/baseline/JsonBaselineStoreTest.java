package ch.fabianaschwanden.sourcescanner.adapter.out.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.BaselineEntry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonBaselineStoreTest {

    private final JsonBaselineStore store = new JsonBaselineStore();

    @Test
    void roundtrip_schreibt_und_liest_baseline(@TempDir Path dir) {
        Path file = dir.resolve(".scanner-baseline.json");
        Baseline baseline = new Baseline(1, Instant.parse("2026-06-13T10:00:00Z"),
                List.of(new BaselineEntry("fp-1", "security-team", "2026-06-10", "rotiert, SEC-123")));

        store.write(baseline, file);
        assertTrue(java.nio.file.Files.exists(file));

        Baseline loaded = store.load(file).orElseThrow();
        assertEquals(1, loaded.entries().size());
        assertTrue(loaded.contains("fp-1"));
        assertEquals("security-team", loaded.entries().getFirst().acceptedBy());
    }

    @Test
    void fehlende_datei_liefert_empty(@TempDir Path dir) {
        assertTrue(store.load(dir.resolve("nope.json")).isEmpty());
    }
}
