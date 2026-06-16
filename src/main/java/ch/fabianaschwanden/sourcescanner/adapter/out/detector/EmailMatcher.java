package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * E-Mail-Erkennung (DR-20) mit Test-/Dummy-/Platzhalter-Filter (DR-57): offensichtliche Test-
 * Adressen (reservierte Beispiel-/Test-Domains/-TLDs, bekannte Fixture-Adressen, Test-Namen im
 * Local-Part, exakte Platzhalter-Adressen) sind keine echten Nutzerdaten und werden nicht gemeldet.
 * Die Default-Listen liefert {@link TestEmailDefaults} (aus YAML); die params ergänzen additiv.
 */
final class EmailMatcher implements PiiRuleMatcher {

    private static final Pattern PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private final TestEmailDefaults defaults;

    EmailMatcher(TestEmailDefaults defaults) {
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
     * Nutzerdaten sind, und meldet dafür keinen Fund. Die {@link TestEmailDefaults} werden additiv um
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

        static TestEmailFilter from(TestEmailDefaults defaults, DetectorConfig config,
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
}
