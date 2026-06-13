import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('sourcecode-scanner');
  protected readonly links = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/repositories', label: 'Repositories' },
    { path: '/scans', label: 'Scans' },
    { path: '/findings', label: 'Findings' },
    { path: '/policies', label: 'Policies' },
    { path: '/settings', label: 'Einstellungen' },
  ];
}
