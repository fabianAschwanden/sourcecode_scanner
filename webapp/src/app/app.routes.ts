import { Routes } from '@angular/router';

// Management-UI (Roadmap-Phase 4, docs/06): Dashboard, Repositories, Scans, Findings.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
  },
  {
    path: 'repositories',
    loadComponent: () =>
      import('./features/repositories/repositories-page').then((m) => m.RepositoriesPage),
  },
  {
    path: 'scans',
    loadComponent: () => import('./features/scans/scans-page').then((m) => m.ScansPage),
  },
  {
    path: 'findings',
    loadComponent: () => import('./features/findings/findings-page').then((m) => m.FindingsPage),
  },
  {
    path: 'policies',
    loadComponent: () => import('./features/policies/policies-page').then((m) => m.PoliciesPage),
  },
];
