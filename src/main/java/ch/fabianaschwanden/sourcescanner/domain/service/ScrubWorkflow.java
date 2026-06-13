package ch.fabianaschwanden.sourcescanner.domain.service;

/**
 * Erzwingt die Schritt-/Gate-Reihenfolge der History-Bereinigung (docs/07 §3.3) als reine
 * Domänen-Logik — framework-frei und damit direkt testbar. Die Reihenfolge ist hart:
 * PRECHECK → ROTATION-GATE → BACKUP/MIRROR → SCRUB → VERIFY → DRY-RUN-Freigabe → FORCE-PUSH → POST.
 * Jedes verletzte Gate blockiert; nur ein vollständig grüner Pfad gibt {@code execute} frei.
 */
public final class ScrubWorkflow {

    private ScrubWorkflow() {
    }

    /** Zustand, gegen den die Gates geprüft werden (alles aus dem Request-Kontext aufgelöst, RMR-26/27). */
    public record State(
            boolean repoEnabled,
            boolean hasOpenPullRequests,
            boolean verifiedActiveSecretPresent,
            boolean rotationConfirmed,
            boolean dryRunCompleted,
            boolean forcePushApproved,
            boolean toolAvailable) {

        /** Kopie mit gesetztem Dry-Run-Nachweis (der Service löst diesen separat auf). */
        public State withDryRun(boolean done) {
            return new State(repoEnabled, hasOpenPullRequests, verifiedActiveSecretPresent,
                    rotationConfirmed, done, forcePushApproved, toolAvailable);
        }
    }

    /** Eines der Gates, das blockieren kann. */
    public enum Gate {
        OPT_IN, ROTATION, DRY_RUN, FORCE_PUSH, TOOL
    }

    /** Ergebnis der Prüfung: erlaubt + (falls nicht) das erste verletzte Gate + redigierte Begründung. */
    public record Decision(boolean allowed, Gate blockingGate, String reason) {
        public static Decision allow() {
            return new Decision(true, null, "alle Gates erfüllt");
        }

        public static Decision block(Gate gate, String reason) {
            return new Decision(false, gate, reason);
        }
    }

    /**
     * Prüft, ob ein <b>realer</b> Scrub freigegeben ist. Reihenfolge der Gates ist signifikant: das
     * erste verletzte Gate wird gemeldet (deterministische, nachvollziehbare Begründung im Audit).
     */
    public static Decision evaluateExecute(State s) {
        // 1) Opt-in pro Repo (RMR-02).
        if (!s.repoEnabled()) {
            return Decision.block(Gate.OPT_IN, "History-Scrub für dieses Repo nicht aktiviert (RMR-02)");
        }
        // 2) ROTATION-GATE: ein noch aktives, verifiziertes Secret blockiert hart, bis rotiert (RMR-26).
        if (s.verifiedActiveSecretPresent() && !s.rotationConfirmed()) {
            return Decision.block(Gate.ROTATION,
                    "verifiziert aktives Secret vorhanden — zuerst rotieren (Rotation-Gate, RMR-26)");
        }
        // 3) DRY-RUN-Pflicht vor jedem realen Lauf (RMR-22).
        if (!s.dryRunCompleted()) {
            return Decision.block(Gate.DRY_RUN, "Dry-Run ist vor dem realen Scrub zwingend (RMR-22)");
        }
        // 4) Force-Push-Freigabe getrennt, Admin/Break-Glass (RMR-25/41).
        if (!s.forcePushApproved()) {
            return Decision.block(Gate.FORCE_PUSH, "Force-Push nicht freigegeben (RMR-25/41)");
        }
        // 5) Werkzeug muss vorhanden sein, sonst kein scharfer Lauf (RMR-28).
        if (!s.toolAvailable()) {
            return Decision.block(Gate.TOOL, "Rewrite-Werkzeug nicht verfügbar — nur Dry-Run möglich (RMR-28)");
        }
        return Decision.allow();
    }

    /** Dry-Run ist niedrigschwelliger: nur Opt-in nötig (RMR-02); erzeugt einen Bericht, kein Rewrite. */
    public static Decision evaluateDryRun(State s) {
        if (!s.repoEnabled()) {
            return Decision.block(Gate.OPT_IN, "History-Scrub für dieses Repo nicht aktiviert (RMR-02)");
        }
        return Decision.allow();
    }
}
