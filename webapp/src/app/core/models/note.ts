/** Spiegelt das REST-DTO des Backends (publizierte Sprache), nicht das Domänenmodell. */
export interface Note {
  id: string;
  title: string;
  body: string | null;
  createdAt: string;
}

export interface CreateNoteRequest {
  title: string;
  body: string | null;
}
