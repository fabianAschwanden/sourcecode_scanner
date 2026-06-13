package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SafeRegexTest {

    @Test
    void laengen_guard_greift() {
        assertTrue(SafeRegex.tooLong("x".repeat(SafeRegex.MAX_LINE_LENGTH + 1)));
        assertFalse(SafeRegex.tooLong("kurz"));
        assertFalse(SafeRegex.tooLong(null));
    }

    @Test
    void interruptible_bricht_bei_thread_interrupt_ab() throws Exception {
        // Pathologisches Muster + Interrupt -> RegexInterruptedException statt Hang.
        Pattern evil = Pattern.compile("(a+)+b");
        String input = "a".repeat(40);
        Thread worker = new Thread(() -> {
            CharSequence safe = SafeRegex.interruptible(input);
            assertThrows(SafeRegex.RegexInterruptedException.class, () -> evil.matcher(safe).matches());
        });
        worker.start();
        worker.interrupt();
        worker.join(5_000);
        assertFalse(worker.isAlive(), "Matcher muss durch Interrupt beendet werden");
    }
}
