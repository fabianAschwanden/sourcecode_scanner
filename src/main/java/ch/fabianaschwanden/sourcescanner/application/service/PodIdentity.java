package ch.fabianaschwanden.sourcescanner.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Stabile Identität dieses Pods/JVM-Prozesses für die verteilte Scan-Ausführung. Jeder Pod claimt
 * Läufe unter dieser ID; der Reaper unterscheidet daran lebende von verwaisten Läufen. Default ist
 * eine zufällige UUID je Prozess; auf Fly kann {@code FLY_MACHINE_ID} als sprechende ID dienen.
 */
@ApplicationScoped
public class PodIdentity {

    private final String id;

    public PodIdentity() {
        String machine = System.getenv("FLY_MACHINE_ID");
        String hostname = System.getenv("HOSTNAME");
        String base = machine != null && !machine.isBlank() ? machine
                : (hostname != null && !hostname.isBlank() ? hostname : "pod");
        // Prozess-Suffix, damit zwei JVMs auf demselben Host (z. B. lokal) eindeutig bleiben.
        this.id = (base + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public String id() {
        return id;
    }
}
