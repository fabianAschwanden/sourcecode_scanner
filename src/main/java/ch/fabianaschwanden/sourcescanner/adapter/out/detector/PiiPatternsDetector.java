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
                Severity.HIGH, PiiPatternsDetector::isValidIban),
        CREDITCARD("creditcard", Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"),
                Severity.HIGH, m -> Luhn.isValid(m)),
        EMAIL("email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
                Severity.MEDIUM, m -> true),
        PHONE("phone", Pattern.compile("(?<![\\w.])\\+?\\d[\\d ()/-]{7,16}\\d(?![\\w.])"),
                Severity.MEDIUM, m -> digitCount(m) >= 8 && digitCount(m) <= 15);

        final String key;
        final Pattern pattern;
        final Severity severity;
        final Predicate<String> validator;

        Rule(String key, Pattern pattern, Severity severity, Predicate<String> validator) {
            this.key = key;
            this.pattern = pattern;
            this.severity = severity;
            this.validator = validator;
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
        Set<String> enabled = enabledPatterns(config);
        Map<String, Map<String, Object>> overrides = ruleOverrides(config);
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
                    if (!rule.validator.test(match)) {
                        continue;
                    }
                    // Datums-/Zeitstempel-Treffer sind nie PII (z. B. 2024-01-15, 15.01.2024,
                    // 2024-01-15T12:30:45) und werden ausgefiltert — sie sind immer unbedenklich.
                    if (looksLikeDate(match)) {
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
            rules.add(new DetectorRule(rule.key, rule.key, "PII pattern: " + rule.key, rule.severity));
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

    private Set<String> enabledPatterns(DetectorConfig config) {
        Object raw = config.params().get("patterns");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        }
        Set<String> all = new java.util.HashSet<>();
        for (Rule r : Rule.values()) {
            all.add(r.key);
        }
        return all;
    }

    private static int digitCount(String s) {
        return (int) s.chars().filter(Character::isDigit).count();
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

    /** IBAN-Validierung per ISO-7064 Mod-97 (Prüfziffer == 1). */
    private static boolean isValidIban(String candidate) {
        String iban = candidate.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (iban.length() < 15 || iban.length() > 34) {
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
