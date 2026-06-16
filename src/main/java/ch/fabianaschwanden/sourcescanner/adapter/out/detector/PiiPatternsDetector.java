package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PII-Detektor (DR-20): erkennt Standardmuster IBAN, Kreditkarte, E-Mail, Telefon. Über
 * {@code patterns: [...]} sind einzelne Muster aktivierbar (Default: alle). Kreditkarten werden per
 * {@link Luhn} validiert (DR-22, FP-Reduktion); Treffer werden redigiert (FR-18). Framework-frei
 * testbar (TR-24).
 */
@ApplicationScoped
public class PiiPatternsDetector implements DetectorPort {

    public static final String ID = "pii.patterns";

    private enum Rule {
        IBAN("iban", Pattern.compile("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){3,7}(?:[ ]?[A-Z0-9]{1,3})?\\b"),
                Severity.HIGH, PiiPatternsDetector::isValidIban, true),
        CREDITCARD("creditcard", Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"),
                Severity.HIGH, PiiPatternsDetector::looksLikeCreditCard, true),
        EMAIL("email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
                Severity.MEDIUM, m -> true, true),
        // Telefon: standardmässig AUS (DR-50) — die Heuristik ist zu rauschanfällig (Versionen,
        // IDs, Beträge). Nur aktiv, wenn explizit in patterns gelistet oder per Ruleset aktiviert.
        PHONE("phone", Pattern.compile("(?<![\\w.])\\+?\\d[\\d ()/-]{7,16}\\d(?![\\w.])"),
                Severity.MEDIUM, m -> digitCount(m) >= 8 && digitCount(m) <= 15, false);

        final String key;
        final Pattern pattern;
        final Severity severity;
        final Predicate<String> validator;
        final boolean defaultOn;

        Rule(String key, Pattern pattern, Severity severity, Predicate<String> validator, boolean defaultOn) {
            this.key = key;
            this.pattern = pattern;
            this.severity = severity;
            this.validator = validator;
            this.defaultOn = defaultOn;
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DetectorCategory category() {
        return DetectorCategory.PII;
    }

    @Override
    public boolean supports(FileType type) {
        // PII selten in Binärdateien; alles andere prüfen (Doku/Config/Source enthalten oft Kundendaten).
        return type != FileType.BINARY;
    }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig config) {
        if (!config.enabled()) {
            return List.of();
        }
        Map<String, Map<String, Object>> overrides = ruleOverrides(config);
        Set<String> enabled = enabledPatterns(config, overrides);
        TestEmailFilter testEmails = TestEmailFilter.from(config, overrides.get(Rule.EMAIL.key));
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (SafeRegex.tooLong(line)) {
                continue;
            }
            CharSequence safe = SafeRegex.interruptible(line);
            for (Rule rule : Rule.values()) {
                if (!enabled.contains(rule.key)) {
                    continue;
                }
                Map<String, Object> ov = overrides.get(rule.key);
                // Ruleset-Override: Regel deaktiviert (DR-50) ⇒ überspringen.
                if (ov != null && Boolean.FALSE.equals(ov.get("enabled"))) {
                    continue;
                }
                // Abgleichsmodus (DR-52): bei list/api wird die Muster-Erkennung übersprungen —
                // die wertbasierte Erkennung übernimmt der Datenquellen-Detektor (pii.customer-data-api).
                if (ov != null && isValueMode(ov.get("matchMode"))) {
                    continue;
                }
                Severity severity = severityOf(rule, ov);
                Matcher m = rule.pattern.matcher(safe);
                while (m.find()) {
                    String match = m.group();
                    // Kreditkarten-Kandidat, der in einem längeren Hex-/UUID-Token steckt
                    // (z. B. eine 16er-Zifferngruppe aus 00000000-0000-…-000000002105), ist nie
                    // eine Kartennummer — verwerfen, bevor die Luhn-/Schema-Prüfung greift.
                    if (rule == Rule.CREDITCARD && embeddedInHexToken(line, m.start(), m.end())) {
                        continue;
                    }
                    if (!rule.validator.test(match)) {
                        continue;
                    }
                    // Datums-/Zeitstempel-Treffer sind nie PII (z. B. 2024-01-15, 15.01.2024,
                    // 2024-01-15T12:30:45) und werden ausgefiltert — sie sind immer unbedenklich.
                    if (looksLikeDate(match)) {
                        continue;
                    }
                    // Offensichtliche Test-/Dummy-/Platzhalter-E-Mails sind keine echten Nutzerdaten
                    // (reservierte Beispiel-/Test-Domains, bekannte Fixture-/Docs-Adressen) und werden
                    // ausgefiltert — sie sind immer unbedenklich (DR-57). Die Listen sind über die
                    // params erweiterbar/abschaltbar (TestEmailFilter).
                    if (rule == Rule.EMAIL && testEmails.matches(match)) {
                        continue;
                    }
                    findings.add(new Finding(ID, DetectorCategory.PII, severity, rule.key,
                            unit.path(), i + 1, Redaction.redact(match), unit.commitId(), false));
                }
            }
        }
        return findings;
    }

    @Override
    public List<DetectorRule> rules() {
        List<DetectorRule> rules = new ArrayList<>();
        for (Rule rule : Rule.values()) {
            rules.add(new DetectorRule(rule.key, rule.key, "PII pattern: " + rule.key, rule.severity,
                    rule.defaultOn));
        }
        return rules;
    }

    /** Ruleset-Overrides aus den params (ruleId → {enabled, severity, matchMode, dataSourceName}). */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> ruleOverrides(DetectorConfig config) {
        Object raw = config.params().get("ruleOverrides");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Map<String, Object>>) map;
        }
        return Map.of();
    }

    /** Effektive Severity einer Regel: Override aus dem Ruleset, sonst die Default-Severity (DR-51). */
    private Severity severityOf(Rule rule, Map<String, Object> override) {
        if (override != null && override.get("severity") instanceof String s && !s.isBlank()) {
            try {
                return Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // ungültige Severity ⇒ Default
            }
        }
        return rule.severity;
    }

    /** {@code true}, wenn der Modus LIST/API ist (wertbasiert statt Muster, DR-52). */
    private boolean isValueMode(Object matchMode) {
        return "LIST".equals(matchMode) || "API".equals(matchMode);
    }

    /**
     * Effektiv aktive Muster: explizite {@code patterns}-Liste, falls gesetzt; sonst alle Regeln mit
     * {@code defaultOn} (DR-50). Eine Regel, die im Ruleset ausdrücklich {@code enabled=true} trägt,
     * wird zusätzlich aktiviert — so lässt sich z. B. {@code phone} (standardmässig aus) gezielt
     * wieder einschalten.
     */
    private Set<String> enabledPatterns(DetectorConfig config, Map<String, Map<String, Object>> overrides) {
        Object raw = config.params().get("patterns");
        Set<String> result;
        if (raw instanceof List<?> list && !list.isEmpty()) {
            result = list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(java.util.HashSet::new));
        } else {
            result = new java.util.HashSet<>();
            for (Rule r : Rule.values()) {
                if (r.defaultOn) {
                    result.add(r.key);
                }
            }
        }
        // Im Ruleset ausdrücklich aktivierte Regeln ergänzen (DR-50), auch wenn defaultOn=false.
        overrides.forEach((ruleId, ov) -> {
            if (Boolean.TRUE.equals(ov.get("enabled"))) {
                result.add(ruleId);
            }
        });
        return result;
    }

    private static int digitCount(String s) {
        return (int) s.chars().filter(Character::isDigit).count();
    }

    /**
     * Kreditkarten-Plausibilität (DR-22a, FP-Reduktion). Eine Kartennummer muss <b>alle</b> Kriterien
     * erfüllen, sonst ist sie ein Falsch-Positiv (lange IDs/Timestamps/Tracking-Nr. bestehen oft Luhn):
     * <ol>
     *   <li>ISO-7812 Luhn-Prüfziffer gültig,</li>
     *   <li>keine triviale Folge (lauter gleiche Ziffern),</li>
     *   <li>gültiges Emittenten-Präfix (IIN/BIN) <b>mit</b> passender Länge der jeweiligen Schemata
     *       (Visa, Mastercard inkl. 2-Series, Amex, Discover, Diners, JCB, UnionPay, Maestro).</li>
     * </ol>
     */
    /**
     * {@code true}, wenn der Treffer an {@code [start,end)} Teil eines längeren Hex-/UUID-Tokens ist.
     * Die Kreditkarten-Regex matcht Ziffern über Bindestriche hinweg und greift so versehentlich in
     * UUIDs (z. B. {@code 00000000-0000-0000-0000-000000002105}). Steht direkt vor dem Treffer oder
     * direkt danach ein Hex-Buchstabe ([a-fA-F]) oder ein weiterer Bindestrich, der das Token
     * fortsetzt, handelt es sich um eine ID, nicht um eine Kartennummer.
     */
    private static boolean embeddedInHexToken(String line, int start, int end) {
        if (start > 0) {
            char before = line.charAt(start - 1);
            if (isHexLetter(before) || before == '-') {
                return true;
            }
        }
        if (end < line.length()) {
            char after = line.charAt(end);
            if (isHexLetter(after) || after == '-') {
                return true;
            }
        }
        return false;
    }

    private static boolean isHexLetter(char c) {
        return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean looksLikeCreditCard(String match) {
        if (!Luhn.isValid(match)) {
            return false;
        }
        String d = match.replaceAll("[\\s-]", "");
        if (d.chars().distinct().count() <= 1) {
            return false;
        }
        return matchesIssuerScheme(d);
    }

    /** {@code true}, wenn Präfix + Länge zu einem bekannten Kartenschema passen (IIN/BIN-Quercheck). */
    private static boolean matchesIssuerScheme(String d) {
        int len = d.length();
        int p2 = Integer.parseInt(d.substring(0, 2));
        int p4 = len >= 4 ? Integer.parseInt(d.substring(0, 4)) : -1;
        int p6 = len >= 6 ? Integer.parseInt(d.substring(0, 6)) : -1;
        // Visa: beginnt mit 4, Länge 13/16/19.
        if (d.charAt(0) == '4') {
            return len == 13 || len == 16 || len == 19;
        }
        // American Express: 34/37, Länge 15.
        if ((p2 == 34 || p2 == 37) && len == 15) {
            return true;
        }
        // Mastercard: 51–55 oder 2221–2720, Länge 16.
        if (((p2 >= 51 && p2 <= 55) || (p4 >= 2221 && p4 <= 2720)) && len == 16) {
            return true;
        }
        // Discover: 6011, 65, 644–649, 622126–622925, Länge 16/19.
        if ((p4 == 6011 || p2 == 65 || (p4 >= 6440 && p4 <= 6499)
                || (p6 >= 622126 && p6 <= 622925)) && (len == 16 || len == 19)) {
            return true;
        }
        // Diners Club: 300–305, 3095, 36, 38–39, Länge 14–19.
        if (((p4 >= 3000 && p4 <= 3059) || p4 == 3095 || p2 == 36 || p2 == 38 || p2 == 39)
                && len >= 14 && len <= 19) {
            return true;
        }
        // JCB: 3528–3589, Länge 16–19.
        if (p4 >= 3528 && p4 <= 3589 && len >= 16 && len <= 19) {
            return true;
        }
        // UnionPay: 62, Länge 16–19.
        if (p2 == 62 && len >= 16 && len <= 19) {
            return true;
        }
        // Maestro: 50, 56–69, Länge 12–19 (breite Range -> nur mit gültiger Luhn, bewusst zuletzt).
        if ((p2 == 50 || (p2 >= 56 && p2 <= 69)) && len >= 12 && len <= 19) {
            return true;
        }
        return false;
    }

    /**
     * Datums-/Zeitstempel-Muster, die nie PII sind und immer ausgefiltert werden (z. B. von der
     * Telefon-/Kreditkarten-Heuristik als Falsch-Positiv erfasst). Abgedeckt: ISO {@code 2024-01-15},
     * mit Zeit {@code 2024-01-15T12:30:45} / {@code 2024-01-15 12:30}, sowie {@code 15.01.2024} /
     * {@code 15/01/2024} (DD.MM.YYYY / DD/MM/YYYY) und reine Uhrzeiten {@code 12:30:45}.
     */
    private static final List<Pattern> DATE_PATTERNS = List.of(
            // ISO-Datum, optional gefolgt von (auch unvollständiger) Uhrzeit: 2024-01-15, 2024-01-15 12,
            // 2024-01-15T12:30, 2024-01-15 12:30:45
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{1,2}(?::\\d{2}(?::\\d{2})?)?)?$"),
            // DD.MM.YYYY / DD/MM/YYYY, optional mit Uhrzeit-Anhang
            Pattern.compile("^\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}(?:[T ]\\d{1,2}(?::\\d{2}(?::\\d{2})?)?)?$"),
            // reine Uhrzeit
            Pattern.compile("^\\d{1,2}:\\d{2}(?::\\d{2})?$"));

    /** {@code true}, wenn der Treffer (getrimmt) ein Datum/Zeitstempel ist — dann nie als Fund melden. */
    private static boolean looksLikeDate(String match) {
        String trimmed = match.trim();
        for (Pattern p : DATE_PATTERNS) {
            if (p.matcher(trimmed).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Default-Listen reservierter Beispiel-/Test-Domains und -TLDs, die nie echte Personen adressieren
     * (RFC 2606/6761 {@code example.*}, {@code .test}, {@code .invalid}, {@code .localhost}; private/
     * interne Namen {@code .internal}, {@code .local}; deutscher Platzhalter {@code beispiel.*}) sowie
     * bekannte Fixture-/Docs-Adressen.
     */
    private static final Set<String> DEFAULT_TEST_TLDS = Set.of("test", "invalid", "localhost",
            "example", "internal", "local");
    private static final Set<String> DEFAULT_TEST_SLDS = Set.of("example", "beispiel");
    private static final Set<String> DEFAULT_TEST_DOMAINS = Set.of(
            "googletest.com", "resend.dev", "mailinator.com", "test.com");

    /**
     * Konfigurierbarer Test-/Dummy-/Platzhalter-E-Mail-Filter (DR-57). Erkennt Adressen, die nie echte
     * Nutzerdaten sind (z. B. {@code fabian@example.com}, {@code bot@wm-tippspiel.internal},
     * {@code onboarding@resend.dev}), und meldet dafür keinen Fund. Die Default-Listen werden additiv
     * um die in den params konfigurierten Einträge ergänzt; mit {@code testEmailFilter: false} ist der
     * Filter ganz aus. Params werden sowohl top-level (YAML/CLI-Scan-Konfig) als auch aus dem
     * {@code email}-Ruleset-Override gelesen.
     *
     * <p>Beispiel-params:
     * <pre>{@code testEmailFilter: true, testDomains: [acme-test.io], testTlds: [demo], testSlds: [sandbox]}</pre>
     */
    record TestEmailFilter(boolean enabled, Set<String> domains, Set<String> tlds, Set<String> slds) {

        static TestEmailFilter from(DetectorConfig config, Map<String, Object> emailOverride) {
            Map<String, Object> top = config.params();
            if (Boolean.FALSE.equals(value(top, emailOverride, "testEmailFilter"))) {
                return new TestEmailFilter(false, Set.of(), Set.of(), Set.of());
            }
            return new TestEmailFilter(true,
                    merged(DEFAULT_TEST_DOMAINS, top, emailOverride, "testDomains"),
                    merged(DEFAULT_TEST_TLDS, top, emailOverride, "testTlds"),
                    merged(DEFAULT_TEST_SLDS, top, emailOverride, "testSlds"));
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
     * Offizielle IBAN-Länge je Ländercode (ISO 13616 Registry). Eine echte IBAN hat exakt diese
     * Länge — so schlagen nur länderkonforme Nummern an (deutlich weniger Falsch-Positive als ein
     * blosser 15–34-Bereich).
     */
    private static final Map<String, Integer> IBAN_LENGTHS = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AE", 23), Map.entry("AL", 28), Map.entry("AT", 20),
            Map.entry("AZ", 28), Map.entry("BA", 20), Map.entry("BE", 16), Map.entry("BG", 22),
            Map.entry("BH", 22), Map.entry("BR", 29), Map.entry("BY", 28), Map.entry("CH", 21),
            Map.entry("CR", 22), Map.entry("CY", 28), Map.entry("CZ", 24), Map.entry("DE", 22),
            Map.entry("DK", 18), Map.entry("DO", 28), Map.entry("EE", 20), Map.entry("EG", 29),
            Map.entry("ES", 24), Map.entry("FI", 18), Map.entry("FO", 18), Map.entry("FR", 27),
            Map.entry("GB", 22), Map.entry("GE", 22), Map.entry("GI", 23), Map.entry("GL", 18),
            Map.entry("GR", 27), Map.entry("GT", 28), Map.entry("HR", 21), Map.entry("HU", 28),
            Map.entry("IE", 22), Map.entry("IL", 23), Map.entry("IS", 26), Map.entry("IT", 27),
            Map.entry("JO", 30), Map.entry("KW", 30), Map.entry("KZ", 20), Map.entry("LB", 28),
            Map.entry("LC", 32), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20),
            Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MD", 24), Map.entry("ME", 22),
            Map.entry("MK", 19), Map.entry("MR", 27), Map.entry("MT", 31), Map.entry("MU", 30),
            Map.entry("NL", 18), Map.entry("NO", 15), Map.entry("PK", 24), Map.entry("PL", 28),
            Map.entry("PS", 29), Map.entry("PT", 25), Map.entry("QA", 29), Map.entry("RO", 24),
            Map.entry("RS", 22), Map.entry("SA", 24), Map.entry("SE", 24), Map.entry("SI", 19),
            Map.entry("SK", 24), Map.entry("SM", 27), Map.entry("TN", 24), Map.entry("TR", 26),
            Map.entry("UA", 29), Map.entry("VA", 22), Map.entry("VG", 24), Map.entry("XK", 20));

    /**
     * Echte IBAN-Validierung: korrekt aufgebaut (Ländercode + 2 Prüfziffern + alphanumerisch),
     * <b>länderkonforme Länge</b> (IBAN_LENGTHS) und ISO-7064 Mod-97-Prüfziffer == 1. Nur dann gilt
     * der Treffer als gültige IBAN (DR-21a).
     */
    private static boolean isValidIban(String candidate) {
        String iban = candidate.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (iban.length() < 15 || iban.length() > 34) {
            return false;
        }
        // Format: 2 Buchstaben Ländercode + 2 Prüfziffern + Rest (Buchstaben/Ziffern).
        if (!Character.isLetter(iban.charAt(0)) || !Character.isLetter(iban.charAt(1))
                || !Character.isDigit(iban.charAt(2)) || !Character.isDigit(iban.charAt(3))) {
            return false;
        }
        // Länderkonforme Länge erzwingen — unbekannter Ländercode oder falsche Länge ⇒ keine IBAN.
        Integer expectedLength = IBAN_LENGTHS.get(iban.substring(0, 2));
        if (expectedLength == null || iban.length() != expectedLength) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                return false;
            }
        }
        return mod97(numeric.toString()) == 1;
    }

    /** Mod-97 über eine lange Ziffernfolge (stückweise, ohne BigInteger). */
    private static int mod97(String number) {
        int remainder = 0;
        for (int i = 0; i < number.length(); i++) {
            remainder = (remainder * 10 + (number.charAt(i) - '0')) % 97;
        }
        return remainder;
    }
}
