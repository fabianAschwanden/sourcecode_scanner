package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Remediation-Status eines Fundes (docs/07 §5): von offen über Auto-Fix-PR bis behoben/rotiert/gescrubbt.
 * Ergänzt den Triage-Status um den Behebungs-Workflow.
 */
public enum RemediationStatus {
    OPEN,
    PR_OPEN,
    FIXED,
    ROTATED,
    SCRUBBED
}
