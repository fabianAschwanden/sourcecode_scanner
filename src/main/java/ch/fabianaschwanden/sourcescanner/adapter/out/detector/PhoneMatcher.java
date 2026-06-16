package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.regex.Pattern;

/**
 * Telefonnummer-Erkennung (DR-20). Standardmässig <b>aus</b> (DR-50): die Heuristik ist zu
 * rauschanfällig (Versionen, IDs, Beträge) und schlägt nur an, wenn die Regel explizit (per
 * {@code patterns}-Liste oder Ruleset-Override) aktiviert wird. Ein Treffer gilt nur mit plausibler
 * Ziffernanzahl (8–15) als Fund.
 */
final class PhoneMatcher implements PiiRuleMatcher {

    private static final Pattern PATTERN = Pattern.compile("(?<![\\w.])\\+?\\d[\\d ()/-]{7,16}\\d(?![\\w.])");

    @Override
    public String key() {
        return "phone";
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
        return false;
    }

    @Override
    public boolean accepts(String match, String line, int start, int end,
            EmailMatcher.TestEmailFilter emailFilter) {
        int digits = digitCount(match);
        return digits >= 8 && digits <= 15;
    }

    private static int digitCount(String s) {
        return (int) s.chars().filter(Character::isDigit).count();
    }
}
