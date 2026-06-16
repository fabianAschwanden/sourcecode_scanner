package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Default-Listen für den Test-/Dummy-/Platzhalter-E-Mail-Filter (DR-57), aus YAML geladen statt im
 * Code verdrahtet. Felder: {@code tlds}, {@code slds}, {@code domains}, {@code localParts},
 * {@code addresses}. Die Detektor-/Ruleset-params ergänzen diese Listen additiv (siehe
 * {@link EmailMatcher.TestEmailFilter}).
 *
 * <p>Pfad über {@code scan.detectors.pii.testEmailsFile} konfigurierbar; fehlt die Datei, gilt ein
 * eingebauter Fallback (damit der Filter nie still leerläuft).
 */
final class TestEmailDefaults {

    private static final Logger LOG = Logger.getLogger(TestEmailDefaults.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    final Set<String> tlds;
    final Set<String> slds;
    final Set<String> domains;
    final Set<String> localParts;
    final Set<String> addresses;

    TestEmailDefaults(Set<String> tlds, Set<String> slds, Set<String> domains,
            Set<String> localParts, Set<String> addresses) {
        this.tlds = tlds;
        this.slds = slds;
        this.domains = domains;
        this.localParts = localParts;
        this.addresses = addresses;
    }

    /** Eingebauter Fallback, falls keine Datei konfiguriert/vorhanden ist (Verhalten bleibt stabil). */
    static TestEmailDefaults builtIn() {
        return new TestEmailDefaults(
                Set.of("test", "invalid", "localhost", "example", "internal", "local"),
                Set.of("example", "beispiel"),
                Set.of("googletest.com", "resend.dev", "mailinator.com", "test.com"),
                Set.of("test", "tester", "testuser", "testaccount", "testperson", "dummy", "example",
                        "mustermann", "musterfrau", "musterkunde", "healthcheck", "noreply", "no-reply",
                        "donotreply", "do-not-reply"),
                Set.of("mail@mail.com"));
    }

    /** Lädt die Defaults aus der konfigurierten Datei; ohne Konfiguration/Datei: {@link #builtIn()}. */
    static TestEmailDefaults fromConfiguredFile() {
        String path = ConfigProvider.getConfig()
                .getOptionalValue("scan.detectors.pii.testEmailsFile", String.class)
                .orElse(null);
        if (path == null || path.isBlank()) {
            return builtIn();
        }
        return fromFile(Path.of(path.trim()));
    }

    static TestEmailDefaults fromFile(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.warnf("test-email defaults file not found, using built-in list: %s", file);
            return builtIn();
        }
        try {
            JsonNode root = YAML.readTree(Files.readString(file));
            return new TestEmailDefaults(
                    values(root.get("tlds")), values(root.get("slds")), values(root.get("domains")),
                    values(root.get("localParts")), values(root.get("addresses")));
        } catch (Exception e) {
            LOG.warnf(e, "could not read test-email defaults %s — using built-in list", file);
            return builtIn();
        }
    }

    private static Set<String> values(JsonNode list) {
        Set<String> out = new LinkedHashSet<>();
        if (list != null && list.isArray()) {
            for (JsonNode entry : list) {
                if (entry.isTextual()) {
                    out.add(entry.asText().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }
}
