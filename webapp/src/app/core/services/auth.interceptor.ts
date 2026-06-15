import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Leitet bei 401 (nicht authentifiziert) auf die öffentliche Landing-Seite ({@code /}) um (WR-30).
 * Greift, wenn die SPA geschützte API-Endpunkte ohne gültige Session aufruft. {@code /api/me} ist
 * ausgenommen — dessen 401 ist der normale „nicht eingeloggt"-Fall (AuthService). Auf den öffentlichen
 * Seiten ({@code /}, {@code /login}) wird nicht umgeleitet.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err) => {
      const path = location.pathname;
      if (
        err?.status === 401 &&
        !req.url.endsWith('/api/me') &&
        path !== '/' &&
        !path.startsWith('/login')
      ) {
        location.assign('/');
      }
      return throwError(() => err);
    }),
  );
