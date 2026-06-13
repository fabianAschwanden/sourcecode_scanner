package ch.example.app.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

/** Eingabe-DTO — früh am Systemrand validieren, internem Code vertrauen. */
public record CreateNoteRequest(@NotBlank String title, String body) {
}
