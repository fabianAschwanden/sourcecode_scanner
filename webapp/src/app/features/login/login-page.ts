import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Eigene Login-Landing-Seite im GitHub-Dark-Stil (WR-30). Öffentlich erreichbar; der „Sign in with
 * GitHub"-Button navigiert per Vollseiten-Redirect auf den authentifizierten Backend-Pfad
 * {@code /login}, der den OIDC-Flow zum IdP (GitHub) anstösst. Kein Angular-Router hier, damit der
 * Server den Redirect übernimmt.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center bg-canvas px-4 text-fg">
      <div
        class="w-full max-w-sm rounded-lg border border-default bg-surface p-8 text-center shadow-sm"
      >
        <svg
          class="mx-auto mb-4 h-12 w-12 text-fg"
          viewBox="0 0 16 16"
          fill="currentColor"
          aria-hidden="true"
        >
          <path
            d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
          />
        </svg>
        <h1 class="text-xl font-semibold">{{ t('app.title') }}</h1>
        <p class="mt-2 mb-6 text-sm text-muted">{{ t('login.intro') }}</p>
        <a
          href="/login"
          class="inline-flex w-full items-center justify-center gap-2 rounded border border-default bg-canvas px-4 py-2 text-sm font-medium hover:border-accent hover:text-accent"
        >
          <svg class="h-4 w-4" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
            />
          </svg>
          {{ t('login.github') }}
        </a>
        <p class="mt-4 text-xs text-muted">{{ t('login.note') }}</p>
      </div>
    </main>
  `,
})
export class LoginPage {
  private readonly i18n = inject(I18nService);

  protected t(key: string): string {
    return this.i18n.t(key);
  }
}
