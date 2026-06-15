import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, tap } from 'rxjs';

export interface CurrentUser {
  readonly login: string;
  readonly roles: string[];
}

/**
 * Authentifizierungs-Zustand der UI (WR-30/31). Lädt den aktuellen Nutzer über {@code /api/me}; ist
 * niemand eingeloggt (401), bleibt {@code user()} null und die Shell zeigt den Login-Hinweis. Logout
 * via Vollseiten-Navigation auf den OIDC-Logout-Pfad.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly user = signal<CurrentUser | null>(null);
  readonly loaded = signal(false);

  /** Lädt den aktuellen Nutzer; 401/Fehler ⇒ nicht eingeloggt (user bleibt null). */
  load(): void {
    this.http
      .get<CurrentUser>('/api/me')
      .pipe(
        tap((u) => this.user.set(u)),
        catchError(() => {
          this.user.set(null);
          return of(null);
        }),
      )
      .subscribe(() => this.loaded.set(true));
  }

  hasRole(role: string): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }
}
