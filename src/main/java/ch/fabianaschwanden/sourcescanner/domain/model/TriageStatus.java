package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Triage-Status eines Fundes im Server-Workflow (WR-12). {@code OPEN} ist der Default; die übrigen
 * Stati werden durch Operator/Admin gesetzt und steuern, ob der Fund das Gate noch bricht.
 */
public enum TriageStatus {
    OPEN,
    BASELINE,
    SUPPRESSED,
    FALSE_POSITIVE;

    /** {@code true}, wenn ein Fund in diesem Status weiterhin als offen/blockierend zählt. */
    public boolean isOpen() {
        return this == OPEN;
    }
}
