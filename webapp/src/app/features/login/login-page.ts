import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { BrandLogo } from '../../shared/brand-logo';

/**
 * Öffentliche Info-/Landing-Seite im GitHub-Startseiten-Stil (WR-30/30a): Hero mit Verlauf + grosser
 * Headline, „Sign in with GitHub"-CTA und Feature-Sektionen, die zeigen, was der Scanner kann. Der
 * Login-Button navigiert per Vollseiten-Redirect auf den authentifizierten Backend-Pfad
 * {@code /login} (startet den OIDC-Flow zu GitHub) — kein Angular-Router, der Server übernimmt.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandLogo],
  template: `
    <main class="min-h-screen bg-canvas text-fg">
      <!-- Top-Bar -->
      <header class="flex items-center justify-between px-6 py-4">
        <div class="flex items-center gap-2">
          <app-brand-logo [size]="28" />
          <span class="font-semibold">{{ t('app.title') }}</span>
        </div>
        <a
          href="/login"
          class="rounded-md border border-default px-3 py-1.5 text-sm font-medium hover:border-accent hover:text-accent"
        >
          {{ t('login.signin') }}
        </a>
      </header>

      <!-- Hero -->
      <section class="relative overflow-hidden">
        <div
          class="pointer-events-none absolute inset-0"
          style="background:
            radial-gradient(60rem 30rem at 50% -8rem, rgba(124,58,237,.45), transparent 60%),
            radial-gradient(40rem 24rem at 80% 0, rgba(47,129,247,.25), transparent 60%);"
        ></div>
        <div class="relative mx-auto max-w-3xl px-6 py-20 text-center sm:py-28">
          <span
            class="inline-block rounded-full border border-default bg-surface/60 px-3 py-1 text-xs text-muted"
          >
            {{ t('login.badge') }}
          </span>
          <h1 class="mt-6 text-4xl font-bold tracking-tight sm:text-6xl">
            {{ t('login.hero.title') }}
          </h1>
          <p class="mx-auto mt-5 max-w-2xl text-lg text-muted">{{ t('login.hero.subtitle') }}</p>
          <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <a
              href="/login"
              class="inline-flex items-center justify-center gap-2 rounded-md bg-accent px-5 py-2.5 text-sm font-semibold text-white hover:bg-accent-emphasis"
            >
              <svg class="h-4 w-4" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                <path
                  d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
                />
              </svg>
              {{ t('login.github') }}
            </a>
            <span class="text-xs text-muted">{{ t('login.note') }}</span>
          </div>
        </div>
      </section>

      <!-- Feature-Grid: was der Scanner kann -->
      <section class="mx-auto max-w-5xl px-6 pb-24">
        <h2 class="mb-2 text-center text-2xl font-semibold">{{ t('login.features.title') }}</h2>
        <p class="mx-auto mb-10 max-w-2xl text-center text-sm text-muted">
          {{ t('login.features.subtitle') }}
        </p>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (f of features; track f.key) {
            <div
              class="rounded-lg border border-default bg-surface p-5 transition hover:border-accent"
            >
              <div
                class="mb-3 flex h-9 w-9 items-center justify-center rounded-md border border-default text-accent"
              >
                <span class="text-lg">{{ f.icon }}</span>
              </div>
              <h3 class="font-semibold">{{ t('login.feature.' + f.key + '.title') }}</h3>
              <p class="mt-1 text-sm text-muted">{{ t('login.feature.' + f.key + '.text') }}</p>
            </div>
          }
        </div>

        <!-- Abschluss-CTA -->
        <div
          class="relative mt-14 overflow-hidden rounded-xl border border-default bg-surface p-10 text-center"
        >
          <div
            class="pointer-events-none absolute inset-0"
            style="background: radial-gradient(40rem 18rem at 50% -6rem, rgba(124,58,237,.35), transparent 60%);"
          ></div>
          <div class="relative">
            <h2 class="text-2xl font-semibold">{{ t('login.cta.title') }}</h2>
            <p class="mx-auto mt-2 max-w-xl text-sm text-muted">{{ t('login.cta.text') }}</p>
            <a
              href="/login"
              class="mt-6 inline-flex items-center justify-center gap-2 rounded-md bg-accent px-5 py-2.5 text-sm font-semibold text-white hover:bg-accent-emphasis"
            >
              {{ t('login.github') }}
            </a>
          </div>
        </div>
      </section>

      <footer class="border-t border-default px-6 py-6 text-center text-xs text-muted">
        {{ t('app.title') }} · {{ t('login.footer') }}
      </footer>
    </main>
  `,
})
export class LoginPage {
  private readonly i18n = inject(I18nService);

  /** Feature-Karten (Schlüssel → i18n {@code login.feature.<key>.title/.text}). */
  protected readonly features = [
    { key: 'secrets', icon: '🔑' },
    { key: 'pii', icon: '🛡️' },
    { key: 'rulesets', icon: '⚙️' },
    { key: 'cicd', icon: '🚦' },
    { key: 'remediation', icon: '🔧' },
    { key: 'insights', icon: '📊' },
  ];

  protected t(key: string): string {
    return this.i18n.t(key);
  }
}
