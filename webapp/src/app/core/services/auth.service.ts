import { Injectable, signal } from '@angular/core';

export interface CurrentUser {
  readonly login: string;
  readonly roles: string[];
}

/**
 * Authentifizierungs-Zustand der UI (WR-30/31). Prüft beim Start {@code /api/me}: liefert es den
 * Nutzer (200), ist man eingeloggt; jede andere Antwort (401/403 oder ein 302-Redirect des IdP, der
 * per {@code redirect: 'manual'} als opaque-redirect ankommt) bedeutet „nicht eingeloggt" und leitet
 * — ausserhalb der öffentlichen Login-Seite — auf {@code /login}. Logout via Vollseiten-Navigation.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<CurrentUser | null>(null);
  readonly loaded = signal(false);

  async load(): Promise<void> {
    try {
      // redirect: 'manual' verhindert, dass fetch dem IdP-302 (zu GitHub) folgt; ein solcher Redirect
      // kommt dann als type 'opaqueredirect' / status 0 zurück = nicht eingeloggt.
      const res = await fetch('/api/me', {
        redirect: 'manual',
        headers: { Accept: 'application/json' },
      });
      if (res.ok) {
        this.user.set((await res.json()) as CurrentUser);
      } else {
        this.user.set(null);
        this.redirectToLogin();
      }
    } catch {
      this.user.set(null);
      this.redirectToLogin();
    } finally {
      this.loaded.set(true);
    }
  }

  hasRole(role: string): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  private redirectToLogin(): void {
    if (!location.pathname.startsWith('/login')) {
      location.assign('/login');
    }
  }
}
