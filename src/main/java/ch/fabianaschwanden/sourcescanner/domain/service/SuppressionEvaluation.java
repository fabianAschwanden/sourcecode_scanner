package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.SuppressionRule;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wertet Suppressions aus (FR-10, DR-40, docs/03 §2/§4): Pfad-Globs auf aggregierten Funden sowie
 * Inline-Direktiven an der Fundstelle. Pure Domänen-Logik, framework-frei.
 *
 * <p>Inline-Direktiven (docs/03 §4): {@code scanner:ignore-secret|ignore-pii|ignore-line|ignore-next-line}
 * mit optionalem {@code reason="…"}. Eine Pflicht-Begründung kann erzwungen werden (NFR-20).
 */
public final class SuppressionEvaluation {

    private static final Pattern DIRECTIVE = Pattern.compile(
            "scanner:(ignore-secret|ignore-pii|ignore-line|ignore-next-line)(?:\\s+reason=\"([^\"]*)\")?");

    private SuppressionEvaluation() {
    }

    /** Markiert Funde als unterdrückt, deren Datei einer Pfad-Regel (für ihren Detektor) entspricht. */
    public static List<AggregatedFinding> applyPathRules(List<AggregatedFinding> findings,
                                                         List<SuppressionRule> rules,
                                                         java.util.function.Function<String, String> detectorGroup) {
        return findings.stream()
                .map(f -> matchesAnyPath(f, rules, detectorGroup) ? f.asSuppressed() : f)
                .toList();
    }

    private static boolean matchesAnyPath(AggregatedFinding f, List<SuppressionRule> rules,
                                          java.util.function.Function<String, String> detectorGroup) {
        String file = f.finding().file();
        String detectorId = f.finding().detectorId();
        String group = detectorGroup.apply(detectorId);
        Path path = Path.of(file);
        for (SuppressionRule rule : rules) {
            if (matchesGlob(rule.pathGlob(), path) && rule.matchesDetector(detectorId, group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Glob-Match mit gitignore-naher Semantik: {@code **&#47;Config.java} trifft auch eine
     * Datei ohne Verzeichnis. Java-Glob behandelt ein führendes {@code **&#47;} als Pflicht-Segment,
     * daher wird zusätzlich die Variante ohne diesen Präfix geprüft.
     */
    private static boolean matchesGlob(String glob, Path path) {
        if (matches(glob, path)) {
            return true;
        }
        if (glob.startsWith("**/")) {
            return matches(glob.substring(3), path);
        }
        return false;
    }

    private static boolean matches(String glob, Path path) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        return matcher.matches(path);
    }

    /**
     * Prüft eine Inline-Direktive für einen Fund in {@code category} auf {@code findingLine}; mit
     * {@code previousLine} für {@code ignore-next-line}. {@code requireReason}: Direktiven ohne
     * {@code reason="…"} unterdrücken dann nicht (NFR-20).
     */
    public static boolean isInlineSuppressed(String findingLine, String previousLine,
                                             DetectorCategory category, boolean requireReason) {
        return matchesDirective(findingLine, category, requireReason, false)
                || matchesDirective(previousLine, category, requireReason, true);
    }

    private static boolean matchesDirective(String line, DetectorCategory category,
                                            boolean requireReason, boolean fromPreviousLine) {
        if (line == null) {
            return false;
        }
        Matcher m = DIRECTIVE.matcher(line);
        while (m.find()) {
            String directive = m.group(1).toLowerCase(Locale.ROOT);
            String reason = m.group(2);
            if (requireReason && (reason == null || reason.isBlank())) {
                continue;
            }
            boolean applies = switch (directive) {
                case "ignore-next-line" -> fromPreviousLine;
                case "ignore-line" -> !fromPreviousLine;
                case "ignore-secret" -> !fromPreviousLine && category == DetectorCategory.SECRET;
                case "ignore-pii" -> !fromPreviousLine && category == DetectorCategory.PII;
                default -> false;
            };
            if (applies) {
                return true;
            }
        }
        return false;
    }
}
