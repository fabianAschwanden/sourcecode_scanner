import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NotesPage } from './notes-page';
import { Note } from '../../core/models/note';

describe('NotesPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('zeigt geladene Notes an', async () => {
    const fixture = TestBed.createComponent(NotesPage);
    const httpMock = TestBed.inject(HttpTestingController);

    const notes: Note[] = [
      { id: '1', title: 'Erste Note', body: 'Inhalt', createdAt: '2026-01-01T00:00:00Z' },
    ];
    httpMock.expectOne('/api/notes').flush(notes);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Erste Note');
    httpMock.verify();
  });
});
