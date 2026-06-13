package ch.example.app.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Beispiel-Aggregat des Template-Durchstichs — beim Projektstart durch echte Fachmodelle ersetzen.
 * Immutable record, Invarianten im Compact-Constructor (fail fast), keine Framework-Imports.
 */
public record Note(UUID id, String title, String body, Instant createdAt) {

    public Note {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
