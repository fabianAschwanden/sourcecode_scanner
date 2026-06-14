import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService } from './core/i18n/i18n.service';
import { Lang } from './core/i18n/translations';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly i18n = inject(I18nService);
  protected readonly links = [
    { path: '/dashboard', key: 'nav.dashboard' },
    { path: '/repositories', key: 'nav.repositories' },
    { path: '/scans', key: 'nav.scans' },
    { path: '/findings', key: 'nav.findings' },
    { path: '/datasources', key: 'nav.datasources' },
    { path: '/policies', key: 'nav.policies' },
    { path: '/settings', key: 'nav.settings' },
  ];

  protected t(key: string): string {
    return this.i18n.t(key);
  }

  protected switchLang(lang: string): void {
    this.i18n.setLang(lang as Lang);
  }
}
