package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

/**
 * Schutz gegen pathologisches Regex-Backtracking (ReDoS, NFR-04). Bündelt zwei leichtgewichtige
 * Massnahmen, die die Detektoren teilen:
 *
 * <ul>
 *   <li>{@link #tooLong(String)} — Längen-Guard: überlange Zeilen werden übersprungen.</li>
 *   <li>{@link #interruptible(CharSequence)} — eine {@link CharSequence}, deren {@code charAt} bei
 *       gesetztem Thread-Interrupt abbricht. So bricht der Worker-Pool-Timeout (NFR-06) auch einen
 *       laufenden {@code Matcher} ab, statt zu hängen.</li>
 * </ul>
 */
public final class SafeRegex {

    /** Zeilen länger als dies werden nicht gematcht (Schutz vor teurem Backtracking). */
    public static final int MAX_LINE_LENGTH = 4_000;

    private SafeRegex() {
    }

    public static boolean tooLong(String line) {
        return line != null && line.length() > MAX_LINE_LENGTH;
    }

    /** Wickelt {@code input} so ein, dass ein laufender Matcher auf Thread-Interrupt reagiert. */
    public static CharSequence interruptible(CharSequence input) {
        return new InterruptibleCharSequence(input);
    }

    private record InterruptibleCharSequence(CharSequence delegate) implements CharSequence {

        @Override
        public char charAt(int index) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RegexInterruptedException();
            }
            return delegate.charAt(index);
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new InterruptibleCharSequence(delegate.subSequence(start, end));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /** Signalisiert, dass ein Match wegen Thread-Interrupt (Timeout) abgebrochen wurde. */
    public static final class RegexInterruptedException extends RuntimeException {
        public RegexInterruptedException() {
            super("regex matching interrupted (timeout)");
        }
    }
}
