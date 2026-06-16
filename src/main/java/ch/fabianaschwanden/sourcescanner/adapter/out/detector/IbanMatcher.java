package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * IBAN-Erkennung (DR-20/21a): echte Validierung statt blossem Muster — korrekt aufgebaut
 * (Ländercode + 2 Prüfziffern + alphanumerisch), länderkonforme Länge (ISO-13616-Registry) und
 * ISO-7064 Mod-97-Prüfziffer == 1. Bekannte Beispiel-/Test-IBANs werden zusätzlich per Allowlist
 * unterdrückt (DR-23).
 */
final class IbanMatcher implements PiiRuleMatcher {

    private static final Pattern PATTERN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){3,7}(?:[ ]?[A-Z0-9]{1,3})?\\b");

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

    private final PiiAllowlist allowlist;

    IbanMatcher(PiiAllowlist allowlist) {
        this.allowlist = allowlist;
    }

    @Override
    public String key() {
        return "iban";
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
        return isValidIban(match) && !allowlist.contains(key(), match);
    }

    /**
     * Echte IBAN-Validierung: korrekt aufgebaut (Ländercode + 2 Prüfziffern + alphanumerisch),
     * <b>länderkonforme Länge</b> (IBAN_LENGTHS) und ISO-7064 Mod-97-Prüfziffer == 1 (DR-21a).
     */
    static boolean isValidIban(String candidate) {
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
