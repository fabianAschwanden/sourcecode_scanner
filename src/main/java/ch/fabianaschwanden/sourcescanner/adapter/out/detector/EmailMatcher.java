package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * E-Mail-Erkennung (DR-20) mit Test-/Dummy-/Platzhalter-Filter (DR-57): offensichtliche Test-
 * Adressen (reservierte Beispiel-/Test-Domains/-TLDs, bekannte Fixture-Adressen, Test-Namen im
 * Local-Part, exakte Platzhalter-Adressen) sind keine echten Nutzerdaten und werden nicht gemeldet.
 * Die Default-Listen liefert {@link Defaults} (aus YAML); die params ergänzen additiv.
 */
final class EmailMatcher implements PiiRuleMatcher {

    private static final Pattern PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private final Defaults defaults;

    EmailMatcher(Defaults defaults) {
        this.defaults = defaults;
    }

    @Override
    public String key() {
        return "email";
    }

    @Override
    public Pattern pattern() {
        return PATTERN;
    }

    @Override
    public Severity defaultSeverity() {
        return Severity.MEDIUM;
    }

    @Override
    public boolean defaultOn() {
        return true;
    }

    @Override
    public boolean accepts(String match, String line, int start, int end, TestEmailFilter emailFilter) {
        // Ein echter Treffer ist alles, was NICHT als Test-/Platzhalter-Adresse erkannt wird.
        return emailFilter == null || !emailFilter.matches(match);
    }

    /** Baut den pro Scan gültigen Filter aus den Defaults + params/override (additiv). */
    TestEmailFilter filterFrom(DetectorConfig config, Map<String, Object> emailOverride) {
        return TestEmailFilter.from(defaults, config, emailOverride);
    }

    /**
     * Konfigurierbarer Test-/Dummy-/Platzhalter-E-Mail-Filter (DR-57). Erkennt Adressen, die nie echte
     * Nutzerdaten sind, und meldet dafür keinen Fund. Die {@link Defaults} werden additiv um
     * die in den params konfigurierten Einträge ergänzt; mit {@code testEmailFilter: false} ist der
     * Filter ganz aus. Params werden top-level (YAML/CLI-Scan-Konfig) und aus dem {@code email}-
     * Ruleset-Override gelesen.
     *
     * <p>Beispiel-params:
     * <pre>{@code testEmailFilter: true, testDomains: [acme-test.io], testTlds: [demo],
     * testSlds: [sandbox], testLocalParts: [robot, qa], testAddresses: [demo@firma.ch]}</pre>
     */
    record TestEmailFilter(boolean enabled, Set<String> domains, Set<String> tlds, Set<String> slds,
            Set<String> localParts, Set<String> addresses) {

        static TestEmailFilter from(Defaults defaults, DetectorConfig config,
                Map<String, Object> emailOverride) {
            Map<String, Object> top = config.params();
            if (Boolean.FALSE.equals(value(top, emailOverride, "testEmailFilter"))) {
                return new TestEmailFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
            }
            return new TestEmailFilter(true,
                    merged(defaults.domains, top, emailOverride, "testDomains"),
                    merged(defaults.tlds, top, emailOverride, "testTlds"),
                    merged(defaults.slds, top, emailOverride, "testSlds"),
                    merged(defaults.localParts, top, emailOverride, "testLocalParts"),
                    merged(defaults.addresses, top, emailOverride, "testAddresses"));
        }

        /** {@code true} für eine offensichtliche Test-/Platzhalter-Adresse (fall-insensitiv). */
        boolean matches(String email) {
            if (!enabled) {
                return false;
            }
            int at = email.lastIndexOf('@');
            if (at < 0 || at == email.length() - 1) {
                return false;
            }
            // Vollständige bekannte Platzhalter-Adresse (z. B. mail@mail.com) exakt abgleichen.
            if (addresses.contains(email.toLowerCase(Locale.ROOT))) {
                return true;
            }
            // Local-Part (vor dem @) tokenweise gegen bekannte Test-Namen prüfen — greift auch bei
            // echten Domains (z. B. thorsten.tester@css.ch, erika.musterfrau@css.ch).
            String local = email.substring(0, at).toLowerCase(Locale.ROOT);
            for (String token : local.split("[._+-]")) {
                if (localParts.contains(token)) {
                    return true;
                }
            }
            String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
            if (domains.contains(domain)) {
                return true;
            }
            String[] labels = domain.split("\\.");
            if (labels.length == 0) {
                return false;
            }
            if (tlds.contains(labels[labels.length - 1])) {
                return true;
            }
            // Second-Level-Domain (z. B. example.com / beispiel.de) als Platzhalter behandeln.
            return labels.length >= 2 && slds.contains(labels[labels.length - 2]);
        }

        /** params-Wert: erst top-level, dann email-Override (Override gewinnt, falls beides gesetzt). */
        private static Object value(Map<String, Object> top, Map<String, Object> override, String key) {
            if (override != null && override.get(key) != null) {
                return override.get(key);
            }
            return top.get(key);
        }

        /**
         * Vereinigt die Default-Liste mit den (optional) konfigurierten, kleingeschriebenen Einträgen aus
         * top-level params und email-Override (additiv, DR-57). Fehlen beide, gilt nur der Default.
         */
        private static Set<String> merged(Set<String> defaults, Map<String, Object> top,
                Map<String, Object> override, String key) {
            Set<String> result = null;
            for (Object raw : new Object[] {top.get(key), override == null ? null : override.get(key)}) {
                if (raw instanceof List<?> extra && !extra.isEmpty()) {
                    if (result == null) {
                        result = new java.util.HashSet<>(defaults);
                    }
                    for (Object o : extra) {
                        if (o != null) {
                            result.add(String.valueOf(o).trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
            return result == null ? defaults : result;
        }
    }

    /**
     * Default-Listen für den Test-E-Mail-Filter (DR-57), aus YAML geladen statt im Code verdrahtet.
     * Felder: {@code tlds}, {@code slds}, {@code domains}, {@code localParts}, {@code addresses}.
     * Pfad über {@code scan.detectors.pii.testEmailsFile}; fehlt die Datei, gilt {@link #builtIn()}
     * (damit der Filter nie still leerläuft).
     */
    record Defaults(Set<String> tlds, Set<String> slds, Set<String> domains,
            Set<String> localParts, Set<String> addresses) {

        private static final Logger LOG = Logger.getLogger(EmailMatcher.class);
        private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

        /** Eingebauter Fallback, falls keine Datei konfiguriert/vorhanden ist (Verhalten bleibt stabil). */
        static Defaults builtIn() {
            return new Defaults(
                    Set.of("test", "invalid", "localhost", "example", "internal", "local"),
                    Set.of("example", "beispiel"),
                    Set.of("googletest.com", "resend.dev", "mailinator.com", "test.com"),
                    Set.of("test", "tester", "testuser", "testaccount", "testperson", "dummy", "example",
                            "mustermann", "musterfrau", "musterkunde", "healthcheck", "noreply", "no-reply",
                            "donotreply", "do-not-reply"),
                    Set.of("mail@mail.com"));
        }

        /** Lädt die Defaults aus der konfigurierten Datei; ohne Konfiguration/Datei: {@link #builtIn()}. */
        static Defaults fromConfiguredFile() {
            String path = ConfigProvider.getConfig()
                    .getOptionalValue("scan.detectors.pii.testEmailsFile", String.class)
                    .orElse(null);
            if (path == null || path.isBlank()) {
                return builtIn();
            }
            return fromFile(Path.of(path.trim()));
        }

        static Defaults fromFile(Path file) {
            if (!Files.isRegularFile(file)) {
                LOG.warnf("test-email defaults file not found, using built-in list: %s", file);
                return builtIn();
            }
            try {
                JsonNode root = YAML.readTree(Files.readString(file));
                return new Defaults(
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
}
