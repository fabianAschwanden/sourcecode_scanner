package ch.example.app.domain.port.in;

import ch.example.app.domain.model.Note;

/** Driving Port — Use-Case-Interface. */
public interface CreateNote {

    Note create(String title, String body);
}
