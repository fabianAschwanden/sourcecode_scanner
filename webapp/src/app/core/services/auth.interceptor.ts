import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Leitet bei 401 (nicht authentifiziert) auf die eigene Login-Seite um (WR-30). Greift, wenn die SPA
 * geschützte API-Endpunkte ohne gültige Session aufruft. {@code /api/me} ist ausgenommen — dessen 401
 * ist der normale „nicht eingeloggt"-Fall, den der AuthService selbst behandelt.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err) => {
      if (
        err?.status === 401 &&
        !req.url.endsWith('/api/me') &&
        !location.pathname.startsWith('/login')
      ) {
        location.assign('/login');
      }
      return throwError(() => err);
    }),
  );
