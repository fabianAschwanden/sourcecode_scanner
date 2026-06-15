import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { ManagedSecret, SecretStorageMode, Settings, Severity } from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';
import { PageTitle } from '../../shared/page-title';

/** Systemweite Einstellungen (WR-15..18): allgemeine E-Mail, Defaults, Secret-Referenz-Status. */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PageTitle],
  template: `
    <section class="p-6">
      <app-page-title>{{ t('settings.title') }}</app-page-title>

      @if (settings(); as s) {
        <form (ngSubmit)="save()" class="mb-6 grid max-w-xl gap-3">
          <label class="text-sm text-muted">
            {{ t('settings.email') }}
            <input
              [(ngModel)]="email"
              name="email"
              [placeholder]="t('settings.email.placeholder')"
              [title]="t('settings.email.tooltip')"
              class="mt-1 w-full rounded border border-default px-2 py-1"
            />
          </label>
          <label class="text-sm text-muted">
            {{ t('settings.defaultFailOn') }}
            <select
              [(ngModel)]="failOn"
              name="failOn"
              [title]="t('settings.defaultFailOn.tooltip')"
              class="mt-1 rounded border border-default px-2 py-1"
            >
              @for (sev of severities; track sev) {
                <option [ngValue]="sev">{{ sev }}</option>
              }
            </select>
          </label>
          <label class="text-sm text-muted">
            {{ t('settings.defaultScanMode') }}
            <select
              [(ngModel)]="scanMode"
              name="scanMode"
              [title]="t('settings.scanMode.tooltip')"
              class="mt-1 rounded border border-default px-2 py-1"
            >
              <option value="full">full</option>
              <option value="incremental">incremental</option>
            </select>
          </label>
          <label class="text-sm text-muted">
            {{ t('settings.retentionDays') }}
            <input
              type="number"
              [(ngModel)]="retentionDays"
              name="retentionDays"
              [title]="t('settings.retentionDays.tooltip')"
              class="mt-1 w-32 rounded border border-default px-2 py-1"
            />
          </label>
          <div>
            <button
              type="submit"
              class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
            >
              {{ t('common.save') }}
            </button>
            @if (saved()) {
              <span class="ml-3 text-sm text-muted">{{ t('common.saved') }}</span>
            }
          </div>
        </form>

        <h3 class="mb-1 font-medium text-fg">{{ t('settings.secrets.title') }}</h3>
        <p class="mb-3 max-w-2xl text-sm text-muted">{{ t('settings.secrets.intro') }}</p>

        <form (ngSubmit)="saveSecret()" class="mb-4 flex flex-wrap items-end gap-2">
          <input
            [(ngModel)]="secretName"
            name="secretName"
            [placeholder]="t('secrets.name')"
            required
            class="rounded border border-default px-2 py-1"
          />
          <select
            [(ngModel)]="secretMode"
            name="secretMode"
            [title]="t('secrets.mode')"
            class="rounded border border-default px-2 py-1"
          >
            <option value="REFERENCE">{{ t('secrets.mode.REFERENCE') }}</option>
            <option value="VAULT_WRITE">{{ t('secrets.mode.VAULT_WRITE') }}</option>
            <option value="DB_ENCRYPTED">{{ t('secrets.mode.DB_ENCRYPTED') }}</option>
          </select>
          @if (secretMode === 'REFERENCE') {
            <input
              [(ngModel)]="secretReference"
              name="secretReference"
              [placeholder]="t('secrets.reference')"
              title="env:GITHUB_TOKEN"
              class="rounded border border-default px-2 py-1"
            />
          } @else {
            <input
              [(ngModel)]="secretValue"
              name="secretValue"
              type="password"
              [placeholder]="t('secrets.value')"
              class="rounded border border-default px-2 py-1"
            />
            @if (secretMode === 'VAULT_WRITE') {
              <input
                [(ngModel)]="secretVaultPath"
                name="secretVaultPath"
                [placeholder]="t('secrets.vaultPath')"
                class="rounded border border-default px-2 py-1"
              />
            }
          }
          <button
            type="submit"
            class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
          >
            {{ t('common.create') }}
          </button>
        </form>

        @if (secretMessage()) {
          <p class="mb-3 rounded border border-default px-3 py-2 text-sm text-muted">
            {{ secretMessage() }}
          </p>
        }

        <table class="w-full max-w-2xl text-sm">
          <thead>
            <tr class="border-b border-default text-left text-muted">
              <th class="py-2">{{ t('secrets.name') }}</th>
              <th>{{ t('secrets.mode') }}</th>
              <th>{{ t('secrets.col.value') }}</th>
              <th>{{ t('secrets.col.resolvable') }}</th>
              <th>{{ t('repos.col.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            @for (sec of secrets(); track sec.id) {
              <tr class="border-b border-default">
                <td class="py-2">{{ sec.name }}</td>
                <td>{{ t('secrets.mode.' + sec.mode) }}</td>
                <td class="font-mono text-xs">
                  {{ sec.mode === 'DB_ENCRYPTED' ? t('secrets.stored') : sec.reference }}
                </td>
                <td
                  [style.color]="sec.resolvable ? 'var(--color-sev-low)' : 'var(--color-sev-high)'"
                >
                  {{ sec.resolvable ? t('common.yes') : t('common.no') }}
                </td>
                <td>
                  <button (click)="deleteSecret(sec)" class="text-sev-high hover:underline">
                    {{ t('common.delete') }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="py-3 text-muted">{{ t('secrets.empty') }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
})
export class SettingsPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected readonly severities: Severity[] = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly settings = signal<Settings | null>(null);
  protected readonly saved = signal(false);

  protected email = '';
  protected failOn: Severity = 'HIGH';
  protected scanMode = 'full';
  protected retentionDays = 365;

  // Verwaltete Secrets (WR-19)
  protected readonly secrets = signal<ManagedSecret[]>([]);
  protected readonly secretMessage = signal<string>('');
  protected secretName = '';
  protected secretMode: SecretStorageMode = 'REFERENCE';
  protected secretReference = '';
  protected secretValue = '';
  protected secretVaultPath = '';

  constructor() {
    this.reload();
    this.reloadSecrets();
  }

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected saveSecret(): void {
    if (!this.secretName.trim()) {
      return;
    }
    const secret: ManagedSecret = {
      id: null,
      name: this.secretName.trim(),
      mode: this.secretMode,
      reference: this.secretMode === 'REFERENCE' ? this.secretReference.trim() : '',
      hasStoredValue: false,
      resolvable: false,
      plaintext: this.secretMode === 'REFERENCE' ? null : this.secretValue,
      vaultPath: this.secretMode === 'VAULT_WRITE' ? this.secretVaultPath.trim() || null : null,
    };
    this.api.saveSecret(secret).subscribe({
      next: () => {
        this.secretMessage.set(this.t('secrets.msg.saved'));
        this.secretName = '';
        this.secretReference = '';
        this.secretValue = '';
        this.secretVaultPath = '';
        this.reloadSecrets();
      },
      error: (err) =>
        this.secretMessage.set(
          this.t('secrets.msg.failed', { error: err?.error?.error ?? this.t('common.error') }),
        ),
    });
  }

  protected deleteSecret(secret: ManagedSecret): void {
    if (!secret.id) {
      return;
    }
    this.api.deleteSecret(secret.id).subscribe(() => this.reloadSecrets());
  }

  private reloadSecrets(): void {
    this.api.secrets().subscribe((list) => this.secrets.set(list));
  }

  protected save(): void {
    const current = this.settings();
    const updated: Settings = {
      generalNotificationEmail: this.email.trim() || null,
      defaultFailOn: this.failOn,
      defaultScanMode: this.scanMode,
      retentionDays: this.retentionDays,
      secretRefs: current?.secretRefs ?? [],
    };
    this.api.saveSettings(updated).subscribe((s) => {
      this.apply(s);
      this.saved.set(true);
    });
  }

  private reload(): void {
    this.api.settings().subscribe((s) => this.apply(s));
  }

  private apply(s: Settings): void {
    this.settings.set(s);
    this.email = s.generalNotificationEmail ?? '';
    this.failOn = s.defaultFailOn;
    this.scanMode = s.defaultScanMode;
    this.retentionDays = s.retentionDays;
  }
}
