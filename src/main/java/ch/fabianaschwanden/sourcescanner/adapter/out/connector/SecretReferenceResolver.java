package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretReferencePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Adapter für {@link SecretReferencePort}: nutzt den {@link CredentialResolver}, um eine Referenz
 * probeweise aufzulösen — gibt nur {@code true}/{@code false} zurück, nie den Wert (WR-17/WR-32).
 */
@ApplicationScoped
public class SecretReferenceResolver implements SecretReferencePort {

    private final CredentialResolver credentials;

    @Inject
    public SecretReferenceResolver(CredentialResolver credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean resolvable(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        try {
            return credentials.resolve(reference).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
