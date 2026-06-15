package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis einer Sammelaktion (WR-23): wie viele Elemente verarbeitet wurden, wie viele erfolgreich
 * waren und — je fehlgeschlagenem Element — die ID samt redigierter Fehlermeldung. So bleibt eine
 * Bulk-Aktion teil-erfolgreich nachvollziehbar, statt komplett zu scheitern.
 */
public record BulkResultDto(int total, int succeeded, List<Failure> failed) {

    public record Failure(String id, String error) {
    }

    /** Sammler, der je Element Erfolg/Fehler aufnimmt und am Ende das DTO baut. */
    public static final class Builder {
        private int total;
        private int succeeded;
        private final List<Failure> failures = new ArrayList<>();

        public void success() {
            total++;
            succeeded++;
        }

        public void failure(String id, String error) {
            total++;
            failures.add(new Failure(id, error == null ? "error" : error));
        }

        public BulkResultDto build() {
            return new BulkResultDto(total, succeeded, List.copyOf(failures));
        }
    }
}
