package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ein Eintrag einer hochgeladenen Key-Value-Liste (IR-67): {@code key} ist der Attributname (z. B.
 * {@code partnernummer}), {@code value} der gesuchte Klartextwert. Der Klartext lebt nur flüchtig
 * beim Verarbeiten des Uploads; persistiert wird ausschliesslich ein Hash des Werts (NFR-23).
 */
public record KeyValuePair(String key, String value) {

    public KeyValuePair {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        value = value == null ? "" : value;
    }
}
