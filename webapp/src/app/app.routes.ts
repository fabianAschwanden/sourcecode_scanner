import { Routes } from '@angular/router';

// Management-UI (Roadmap-Phase 4, docs/06): Dashboard, Repositories, Scans, Findings.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
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
    path: 'datasources',
    loadComponent: () =>
      import('./features/datasources/datasources-page').then((m) => m.DataSourcesPage),
  },
  {
    path: 'rulesets',
    loadComponent: () => import('./features/rulesets/rulesets-page').then((m) => m.RulesetsPage),
  },
  {
    path: 'policies',
    loadComponent: () => import('./features/policies/policies-page').then((m) => m.PoliciesPage),
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
  },
];
