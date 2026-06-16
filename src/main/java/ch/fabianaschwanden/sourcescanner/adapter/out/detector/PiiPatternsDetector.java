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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PII-Detektor (DR-20): erkennt die Standardmuster IBAN, Kreditkarte, E-Mail und Telefon. Diese
 * Klasse ist nur der <b>Orchestrator</b> — je PII-Typ gibt es einen eigenen {@link PiiRuleMatcher}
 * ({@link IbanMatcher}, {@link CreditCardMatcher}, {@link EmailMatcher}, {@link PhoneMatcher}), der
 * Muster und Plausibilität/FP-Filter kapselt. Hier liegt nur die gemeinsame Logik: aktive Regeln
 * (DR-50), Severity-Override (DR-51), Abgleichsmodus (DR-52), der typübergreifende Datums-/Zeit-
 * stempel-Filter und die Redaktion (FR-18). Framework-frei testbar (TR-24).
 */
@ApplicationScoped
public class PiiPatternsDetector implements DetectorPort {

    public static final String ID = "pii.patterns";

    /** Ein Matcher je PII-Typ; Reihenfolge bestimmt die Auswertungsreihenfolge je Zeile. */
    private final List<PiiRuleMatcher> matchers;
    private final EmailMatcher emailMatcher;

    public PiiPatternsDetector() {
        this(PiiAllowlist.fromConfiguredFile(), TestEmailDefaults.fromConfiguredFile());
    }

    PiiPatternsDetector(PiiAllowlist allowlist) {
        this(allowlist, TestEmailDefaults.builtIn());
    }

    PiiPatternsDetector(PiiAllowlist allowlist, TestEmailDefaults testEmails) {
        this.emailMatcher = new EmailMatcher(testEmails);
        this.matchers = List.of(
                new IbanMatcher(allowlist),
                new CreditCardMatcher(allowlist),
                emailMatcher,
                new PhoneMatcher());
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
        EmailMatcher.TestEmailFilter emailFilter =
                emailMatcher.filterFrom(config, overrides.get(emailMatcher.key()));
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (SafeRegex.tooLong(line)) {
                continue;
            }
            CharSequence safe = SafeRegex.interruptible(line);
            for (PiiRuleMatcher matcher : matchers) {
                if (!enabled.contains(matcher.key())) {
                    continue;
                }
                Map<String, Object> ov = overrides.get(matcher.key());
                // Ruleset-Override: Regel deaktiviert (DR-50) ⇒ überspringen.
                if (ov != null && Boolean.FALSE.equals(ov.get("enabled"))) {
                    continue;
                }
                // Abgleichsmodus (DR-52): bei list/api wird die Muster-Erkennung übersprungen —
                // die wertbasierte Erkennung übernimmt der Datenquellen-Detektor (pii.customer-data-api).
                if (ov != null && isValueMode(ov.get("matchMode"))) {
                    continue;
                }
                Severity severity = severityOf(matcher, ov);
                Matcher m = matcher.pattern().matcher(safe);
                while (m.find()) {
                    String match = m.group();
                    // Datums-/Zeitstempel sind nie PII und werden typübergreifend ausgefiltert
                    // (z. B. 2024-01-15, 15.01.2024) — bevor die typspezifische Prüfung greift.
                    if (looksLikeDate(match)) {
                        continue;
                    }
                    if (!matcher.accepts(match, line, m.start(), m.end(), emailFilter)) {
                        continue;
                    }
                    findings.add(new Finding(ID, DetectorCategory.PII, severity, matcher.key(),
                            unit.path(), i + 1, Redaction.redact(match), unit.commitId(), false));
                }
            }
        }
        return findings;
    }

    @Override
    public List<DetectorRule> rules() {
        List<DetectorRule> rules = new ArrayList<>();
        for (PiiRuleMatcher matcher : matchers) {
            rules.add(new DetectorRule(matcher.key(), matcher.key(), "PII pattern: " + matcher.key(),
                    matcher.defaultSeverity(), matcher.defaultOn()));
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
    private Severity severityOf(PiiRuleMatcher matcher, Map<String, Object> override) {
        if (override != null && override.get("severity") instanceof String s && !s.isBlank()) {
            try {
                return Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // ungültige Severity ⇒ Default
            }
        }
        return matcher.defaultSeverity();
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
                    .collect(Collectors.toCollection(HashSet::new));
        } else {
            result = new HashSet<>();
            for (PiiRuleMatcher matcher : matchers) {
                if (matcher.defaultOn()) {
                    result.add(matcher.key());
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

    /**
     * Datums-/Zeitstempel-Muster, die nie PII sind und immer ausgefiltert werden (z. B. von der
     * Telefon-/Kreditkarten-Heuristik als Falsch-Positiv erfasst). Abgedeckt: ISO {@code 2024-01-15},
     * mit Zeit {@code 2024-01-15T12:30:45} / {@code 2024-01-15 12:30}, sowie {@code 15.01.2024} /
     * {@code 15/01/2024} (DD.MM.YYYY / DD/MM/YYYY) und reine Uhrzeiten {@code 12:30:45}.
     */
    private static final List<Pattern> DATE_PATTERNS = List.of(
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{1,2}(?::\\d{2}(?::\\d{2})?)?)?$"),
            Pattern.compile("^\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}(?:[T ]\\d{1,2}(?::\\d{2}(?::\\d{2})?)?)?$"),
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
}
