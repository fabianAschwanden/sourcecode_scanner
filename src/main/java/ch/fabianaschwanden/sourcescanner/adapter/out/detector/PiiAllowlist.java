package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Allowlist bekannter Beispiel-/Test-PII (IBAN/Kreditkarte), die der {@link PiiPatternsDetector}
 * unterdrückt (DR-23, FP-Reduktion). Solche Werte sind absichtlich strukturell gültig (IBAN: Mod-97,
 * Kreditkarte: Luhn) und tauchen in Doku/Tests/SDK-Beispielen auf — sie sind keine echten Konten.
 *
 * <p>Der Abgleich erfolgt auf <b>normalisierter Form</b>: Whitespace/Bindestriche entfernt, Buchstaben
 * (für IBANs) gross. Die Liste wird aus der über {@code scan.detectors.pii.allowlistFile} konfigurierten
 * YAML-Datei geladen; fehlt die Konfiguration/Datei, ist die Allowlist leer (kein Fehler).
 */
final class PiiAllowlist {

    private static final Logger LOG = Logger.getLogger(PiiAllowlist.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final Set<String> ibans;
    private final Set<String> creditCards;

    PiiAllowlist(Set<String> ibans, Set<String> creditCards) {
        this.ibans = ibans;
        this.creditCards = creditCards;
    }

    /** Leere Allowlist (Default, wenn keine Datei konfiguriert ist). */
    static PiiAllowlist empty() {
        return new PiiAllowlist(Set.of(), Set.of());
    }

    /** Lädt die Allowlist aus der konfigurierten Datei; ohne Konfiguration/Datei: leer. */
    static PiiAllowlist fromConfiguredFile() {
        String path = ConfigProvider.getConfig()
                .getOptionalValue("scan.detectors.pii.allowlistFile", String.class)
                .orElse(null);
        if (path == null || path.isBlank()) {
            return empty();
        }
        return fromFile(Path.of(path.trim()));
    }

    static PiiAllowlist fromFile(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.warnf("PII allowlist file not found, ignoring: %s", file);
            return empty();
        }
        try {
            JsonNode root = YAML.readTree(Files.readString(file));
            return new PiiAllowlist(
                    normalizedValues(root.get("iban"), false),
                    normalizedValues(root.get("creditCard"), true));
        } catch (Exception e) {
            LOG.warnf(e, "could not read PII allowlist %s — proceeding without it", file);
            return empty();
        }
    }

    /** {@code true}, wenn der (normalisierte) Treffer der Regel {@code ruleKey} auf der Allowlist steht. */
    boolean contains(String ruleKey, String match) {
        String norm = normalize(match, !"creditcard".equals(ruleKey));
        return switch (ruleKey) {
            case "iban" -> ibans.contains(norm);
            case "creditcard" -> creditCards.contains(norm);
            default -> false;
        };
    }

    private static Set<String> normalizedValues(JsonNode list, boolean digitsOnly) {
        Set<String> out = new HashSet<>();
        if (list != null && list.isArray()) {
            for (JsonNode entry : list) {
                JsonNode value = entry.get("value");
                if (value != null && value.isTextual()) {
                    out.add(normalize(value.asText(), !digitsOnly));
                }
            }
        }
        return out;
    }

    /** Whitespace/Bindestriche entfernen; bei {@code upper} (IBAN) zusätzlich Grossschreibung. */
    private static String normalize(String raw, boolean upper) {
        String stripped = raw.replaceAll("[\\s-]", "");
        return upper ? stripped.toUpperCase(java.util.Locale.ROOT) : stripped;
    }
}
