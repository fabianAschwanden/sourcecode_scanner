import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateNoteRequest, Note } from '../models/note';

/** REST-Zugriff auf das eigene Backend (BFF). RxJS nur an dieser Grenze. */
@Injectable({ providedIn: 'root' })
export class NotesService {
  private readonly http = inject(HttpClient);

  list(): Observable<Note[]> {
    return this.http.get<Note[]>('/api/notes');
  }

  create(request: CreateNoteRequest): Observable<Note> {
    return this.http.post<Note>('/api/notes', request);
  }
}
