package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Löst Credential-Referenzen auf (NFR-08, IR-30): {@code env:NAME} aus der Umgebung,
 * {@code vault:path#key} ist in Phase 2 ein klar gemeldeter Stub (echte Vault-Anbindung ist
 * Server-Thema, Phase 4). Der Klartext-Token verlässt diese Klasse nur zum authentifizierten Klonen
 * und wird nie geloggt.
 */
@ApplicationScoped
public class CredentialResolver {

    /** Löst eine {@code tokenRef} auf; {@code empty} bei {@code null}/leer (anonymer Zugriff). */
    public Optional<String> resolve(String tokenRef) {
        if (tokenRef == null || tokenRef.isBlank()) {
            return Optional.empty();
        }
        if (tokenRef.startsWith("env:")) {
            String name = tokenRef.substring("env:".length());
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("environment variable not set for tokenRef: env:" + name);
            }
            return Optional.of(value);
        }
        if (tokenRef.startsWith("vault:")) {
            throw new UnsupportedOperationException(
                    "vault: credential references require the server profile (Phase 4); use env: for CLI scans");
        }
        throw new IllegalArgumentException("unsupported tokenRef scheme (expected env: or vault:): " + tokenRef);
    }
}
