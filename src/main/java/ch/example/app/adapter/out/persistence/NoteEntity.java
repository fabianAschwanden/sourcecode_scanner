package ch.example.app.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA-Entity — lebt ausschliesslich im Persistence-Adapter (öffentliche Felder OK). */
@Entity
@Table(name = "note")
public class NoteEntity {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String title;

    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
