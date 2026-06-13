package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Menge akzeptierter Altfunde (docs/03 §5). Wird beim Erstscan generiert und versioniert
 * eingecheckt; nur Funde, deren Fingerprint <b>nicht</b> enthalten ist, brechen bei
 * {@code failOnNewOnly} das Gate (FR-09).
 */
public record Baseline(int version, Instant generatedAt, List<BaselineEntry> entries) {

    public Baseline {
        version = version < 1 ? 1 : version;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static Baseline empty() {
        return new Baseline(1, Instant.now(), List.of());
    }

    public boolean contains(String fingerprint) {
        return entries.stream().anyMatch(e -> e.fingerprint().equals(fingerprint));
    }

    public Set<String> fingerprints() {
        return entries.stream().map(BaselineEntry::fingerprint).collect(Collectors.toSet());
    }
}
