package ch.example.app.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteTest {

    @Test
    void erzeugt_gueltige_note() {
        var id = UUID.randomUUID();
        var note = new Note(id, "Titel", "Inhalt", Instant.EPOCH);

        assertEquals(id, note.id());
        assertEquals("Titel", note.title());
    }

    @Test
    void weist_leeren_titel_zurueck() {
        assertThrows(IllegalArgumentException.class,
                () -> new Note(UUID.randomUUID(), " ", null, Instant.EPOCH));
    }

    @Test
    void weist_fehlende_id_zurueck() {
        assertThrows(IllegalArgumentException.class,
                () -> new Note(null, "Titel", null, Instant.EPOCH));
    }

    @Test
    void weist_fehlendes_erstellungsdatum_zurueck() {
        assertThrows(IllegalArgumentException.class,
                () -> new Note(UUID.randomUUID(), "Titel", null, null));
    }
}
