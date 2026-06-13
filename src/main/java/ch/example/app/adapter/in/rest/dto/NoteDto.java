package ch.example.app.adapter.in.rest.dto;

import ch.example.app.domain.model.Note;
import java.time.Instant;
import java.util.UUID;

/** Transport-Objekt der REST-Schicht — publizierte Sprache des Backends, vom Frontend gespiegelt. */
public record NoteDto(UUID id, String title, String body, Instant createdAt) {

    public static NoteDto from(Note note) {
        return new NoteDto(note.id(), note.title(), note.body(), note.createdAt());
    }
}
