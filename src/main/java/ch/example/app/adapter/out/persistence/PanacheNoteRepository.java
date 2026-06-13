package ch.example.app.adapter.out.persistence;

import ch.example.app.domain.model.Note;
import ch.example.app.domain.port.out.NoteRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven Adapter — implementiert den Port der Domäne, übersetzt zwischen Domänen-Modell und JPA-Entity. */
@ApplicationScoped
public class PanacheNoteRepository implements NoteRepository, PanacheRepositoryBase<NoteEntity, UUID> {

    @Override
    public Note save(Note note) {
        persist(toEntity(note));
        return note;
    }

    @Override
    public List<Note> all() {
        return listAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Note> byId(UUID id) {
        return findByIdOptional(id).map(this::toDomain);
    }

    private NoteEntity toEntity(Note note) {
        var entity = new NoteEntity();
        entity.id = note.id();
        entity.title = note.title();
        entity.body = note.body();
        entity.createdAt = note.createdAt();
        return entity;
    }

    private Note toDomain(NoteEntity entity) {
        return new Note(entity.id, entity.title, entity.body, entity.createdAt);
    }
}
