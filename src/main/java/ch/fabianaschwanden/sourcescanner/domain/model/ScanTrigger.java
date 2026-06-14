package ch.fabianaschwanden.sourcescanner.domain.model;

/** Herkunft eines Scan-Laufs (IR-25): server-/UI-getrieben oder aus einer CI/CD-Pipeline eingeliefert. */
public enum ScanTrigger {
    SERVER,
    CI
}
