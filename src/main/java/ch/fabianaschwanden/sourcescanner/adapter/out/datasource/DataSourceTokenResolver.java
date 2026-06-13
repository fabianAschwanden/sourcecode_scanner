package ch.fabianaschwanden.sourcescanner.adapter.out.datasource;

import java.util.Optional;

/**
 * Löst die Secret-Referenz einer Datenquelle auf (IR-61, NFR-08): {@code env:NAME} aus der Umgebung;
 * {@code vault:} ist hier ein klar gemeldeter Stub (Server-Thema). Eigene, minimale Auflösung, um keine
 * Abhängigkeit auf den Connector-Adapter zu erzeugen (Layering). Der Klartext-Token wird nie geloggt.
 */
final class DataSourceTokenResolver {

    private DataSourceTokenResolver() {
    }

    static Optional<String> resolve(String tokenRef) {
        if (tokenRef == null || tokenRef.isBlank()) {
            return Optional.empty();
        }
        if (tokenRef.startsWith("env:")) {
            String value = System.getenv(tokenRef.substring("env:".length()));
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("environment variable not set for tokenRef: " + tokenRef);
            }
            return Optional.of(value);
        }
        if (tokenRef.startsWith("vault:")) {
            throw new UnsupportedOperationException(
                    "vault: references require the server secret store; use env: otherwise");
        }
        throw new IllegalArgumentException("unsupported tokenRef scheme (expected env: or vault:): " + tokenRef);
    }
}
