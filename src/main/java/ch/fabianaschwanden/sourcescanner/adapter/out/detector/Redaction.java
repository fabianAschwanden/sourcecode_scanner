package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

/**
 * Maskiert Treffer, bevor sie in ein {@code Finding} gelangen (FR-18, NFR-09): zeigt erste/letzte
 * vier Zeichen, ersetzt den Rest durch {@code *}. Kurze Werte werden vollständig maskiert.
 */
public final class Redaction {

    private static final int VISIBLE = 4;

    private Redaction() {
    }

    public static String redact(String match) {
        if (match == null) {
            return "";
        }
        int len = match.length();
        if (len <= 2 * VISIBLE) {
            return "*".repeat(len);
        }
        String head = match.substring(0, VISIBLE);
        String tail = match.substring(len - VISIBLE);
        return head + "*".repeat(len - 2 * VISIBLE) + tail;
    }
}
