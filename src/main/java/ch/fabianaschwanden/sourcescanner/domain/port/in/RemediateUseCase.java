package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;
import java.util.UUID;

/** Driving Port — Auto-Fix per PR/MR für einen Fund (RMR-10). Opt-in, RBAC Operator/Admin (RMR-40). */
public interface RemediateUseCase {

    /**
     * Erzeugt aus dem Fund (und seiner Quelle) einen Auto-Fix-PR/MR. Wirft, wenn Remediation für das
     * Repo/global deaktiviert ist oder kein Fix-Vorschlag erzeugt werden kann.
     */
    PrRef remediate(UUID findingId, String actor);
}
