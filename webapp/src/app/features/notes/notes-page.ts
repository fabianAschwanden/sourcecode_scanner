import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NotesService } from '../../core/services/notes.service';
import { Note } from '../../core/models/note';

@Component({
  selector: 'app-notes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto max-w-2xl space-y-6 p-6">
      <h2 class="text-xl font-semibold">Notes</h2>

      <form class="flex gap-2" (submit)="create($event, title, body)">
        <input
          #title
          name="title"
          placeholder="Titel"
          required
          class="w-48 rounded border border-gray-300 px-3 py-2"
        />
        <input
          #body
          name="body"
          placeholder="Text"
          class="flex-1 rounded border border-gray-300 px-3 py-2"
        />
        <button type="submit" class="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          Anlegen
        </button>
      </form>

      <ul class="divide-y divide-gray-200 rounded border border-gray-200">
        @for (note of notes(); track note.id) {
          <li class="p-3">
            <p class="font-medium">{{ note.title }}</p>
            @if (note.body) {
              <p class="text-sm text-gray-600">{{ note.body }}</p>
            }
          </li>
        } @empty {
          <li class="p-3 text-sm text-gray-500">Noch keine Notes vorhanden.</li>
        }
      </ul>
    </section>
  `,
})
export class NotesPage {
  private readonly notesService = inject(NotesService);

  protected readonly notes = signal<Note[]>([]);

  constructor() {
    this.reload();
  }

  protected create(event: Event, title: HTMLInputElement, body: HTMLInputElement): void {
    event.preventDefault();
    if (!title.value.trim()) {
      return;
    }
    this.notesService.create({ title: title.value, body: body.value || null }).subscribe(() => {
      title.value = '';
      body.value = '';
      this.reload();
    });
  }

  private reload(): void {
    this.notesService.list().subscribe((notes) => this.notes.set(notes));
  }
}
