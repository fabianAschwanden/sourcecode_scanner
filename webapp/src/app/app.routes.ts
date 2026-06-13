import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/notes/notes-page').then((m) => m.NotesPage),
  },
];
