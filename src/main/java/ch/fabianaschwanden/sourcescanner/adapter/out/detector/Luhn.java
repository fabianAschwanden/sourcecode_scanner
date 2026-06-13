package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

/** Luhn-Prüfsummenvalidierung für Kreditkartennummern zur False-Positive-Reduktion (DR-22). */
public final class Luhn {

    private Luhn() {
    }

    /** {@code true}, wenn die (nur Ziffern, 13–19 Stellen) Nummer die Luhn-Prüfung besteht. */
    public static boolean isValid(String digits) {
        if (digits == null) {
            return false;
        }
        String cleaned = digits.replaceAll("[\\s-]", "");
        if (cleaned.length() < 13 || cleaned.length() > 19 || !cleaned.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = cleaned.length() - 1; i >= 0; i--) {
            int d = cleaned.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
