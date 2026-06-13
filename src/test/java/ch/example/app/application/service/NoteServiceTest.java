package ch.example.app.application.service;

import ch.example.app.domain.model.Note;
import ch.example.app.domain.port.out.NoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit-Test ohne Container: Ports werden gemockt (Blueprint §10). */
@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    NoteRepository noteRepository;

    @Test
    void create_persistiert_neue_note_mit_id_und_zeitstempel() {
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new NoteService(noteRepository);

        var note = service.create("Titel", "Inhalt");

        assertNotNull(note.id());
        assertNotNull(note.createdAt());
        assertEquals("Titel", note.title());
        verify(noteRepository).save(any(Note.class));
    }
}
