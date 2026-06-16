package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.regex.Pattern;

/**
 * Kreditkarten-Erkennung (DR-20/22/22a): Luhn-Prüfziffer, Verwerfen trivialer Folgen, Emittenten-
 * Schema-Quercheck (IIN/BIN-Präfix + schemakonforme Länge) und ein Guard gegen Treffer, die in einem
 * längeren Hex-/UUID-Token stecken. Bekannte Test-PANs werden zusätzlich per Allowlist unterdrückt
 * (DR-23).
 */
final class CreditCardMatcher implements PiiRuleMatcher {

    private static final Pattern PATTERN = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private final PiiAllowlist allowlist;

    CreditCardMatcher(PiiAllowlist allowlist) {
        this.allowlist = allowlist;
    }

    @Override
    public String key() {
        return "creditcard";
    }

    @Override
    public Pattern pattern() {
        return PATTERN;
    }

    @Override
    public Severity defaultSeverity() {
        return Severity.HIGH;
    }

    @Override
    public boolean defaultOn() {
        return true;
    }

    @Override
    public boolean accepts(String match, String line, int start, int end,
            EmailMatcher.TestEmailFilter emailFilter) {
        // Kandidat, der in einem längeren Hex-/UUID-Token steckt (z. B. eine 16er-Zifferngruppe aus
        // 00000000-0000-…-000000002105), ist nie eine Kartennummer.
        if (embeddedInHexToken(line, start, end)) {
            return false;
        }
        return looksLikeCreditCard(match) && !allowlist.contains(key(), match);
    }

    /**
     * Kreditkarten-Plausibilität (DR-22a, FP-Reduktion): ISO-7812 Luhn gültig, keine triviale Folge
     * (lauter gleiche Ziffern) und gültiges Emittenten-Präfix (IIN/BIN) mit passender Länge.
     */
    static boolean looksLikeCreditCard(String match) {
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
     * {@code true}, wenn der Treffer an {@code [start,end)} Teil eines längeren Hex-/UUID-Tokens ist.
     * Die Regex matcht Ziffern über Bindestriche hinweg und greift so versehentlich in UUIDs (z. B.
     * {@code 00000000-0000-0000-0000-000000002105}). Ein direkt angrenzender Hex-Buchstabe oder ein
     * fortsetzender Bindestrich verrät ein ID-Token, keine Kartennummer.
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
}
