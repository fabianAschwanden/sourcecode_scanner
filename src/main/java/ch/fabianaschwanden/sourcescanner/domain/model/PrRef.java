package ch.fabianaschwanden.sourcescanner.domain.model;

/** Referenz auf einen erzeugten Pull/Merge Request (RMR-10). */
public record PrRef(String url, int number) {

    public PrRef {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("PR url must not be blank");
        }
    }
}
