import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { I18nService } from './core/i18n/i18n.service';
import { Lang } from './core/i18n/translations';
import { AuthService } from './core/services/auth.service';
import { BrandLogo } from './shared/brand-logo';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandLogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  /** Header/Nav auf der Login-Seite ausblenden (eigene Landing-Seite, WR-30). */
  protected readonly onLoginPage = signal(false);
  protected readonly links = [
    { path: '/dashboard', key: 'nav.dashboard' },
    { path: '/repositories', key: 'nav.repositories' },
    { path: '/scans', key: 'nav.scans' },
    { path: '/findings', key: 'nav.findings' },
    { path: '/datasources', key: 'nav.datasources' },
    { path: '/rulesets', key: 'nav.rulesets' },
    { path: '/policies', key: 'nav.policies' },
    { path: '/settings', key: 'nav.settings' },
  ];

  constructor() {
    this.auth.load();
    const router = inject(Router);
    this.onLoginPage.set(App.isPublicPage(router.url));
    router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.onLoginPage.set(App.isPublicPage(e.urlAfterRedirects)));
  }

  /** Öffentliche Seiten ohne App-Header/Nav: Landing (/) und Login. */
  private static isPublicPage(url: string): boolean {
    const path = url.split('?')[0];
    return path === '/' || path.startsWith('/login');
  }

  protected t(key: string): string {
    return this.i18n.t(key);
  }

  protected switchLang(lang: string): void {
    this.i18n.setLang(lang as Lang);
  }
}
