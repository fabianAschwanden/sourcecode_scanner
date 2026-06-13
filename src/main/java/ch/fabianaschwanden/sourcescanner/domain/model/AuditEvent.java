package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;

/**
 * Auditierbarer Eintrag einer steuernden Aktion (WR-34): Scan-Start, Triage, Konfig-Änderung.
 * {@code actor} ist die authentifizierte Identität, {@code detail} enthält nur redigierte Angaben.
 */
public record AuditEvent(String actor, String action, String target, String detail, Instant at) {

    public AuditEvent {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("audit action must not be blank");
        }
        actor = actor == null || actor.isBlank() ? "system" : actor;
        at = at == null ? Instant.now() : at;
    }

    public static AuditEvent of(String actor, String action, String target, String detail) {
        return new AuditEvent(actor, action, target, detail, Instant.now());
    }
}
