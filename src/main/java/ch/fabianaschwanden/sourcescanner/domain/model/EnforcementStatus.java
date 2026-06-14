package ch.fabianaschwanden.sourcescanner.domain.model;

/** Wirkungsstatus eines Rulesets (DR-54, analog GitHub): nur {@link #ACTIVE} beeinflusst Scans. */
public enum EnforcementStatus {
    DISABLED,
    ACTIVE
}
