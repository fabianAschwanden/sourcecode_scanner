import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { Settings, Severity } from '../../core/models/scanner';

/** Systemweite Einstellungen (WR-15..18): allgemeine E-Mail, Defaults, Secret-Referenz-Status. */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Einstellungen</h2>

      @if (settings(); as s) {
        <form (ngSubmit)="save()" class="mb-6 grid max-w-xl gap-3">
          <label class="text-sm text-muted">
            Allgemeine Benachrichtigungs-E-Mail
            <input
              [(ngModel)]="email"
              name="email"
              placeholder="security-team@firma.ch"
              title="Adresse für systemweite Meldungen/Sammelreports, z. B. security-team@firma.ch"
              class="mt-1 w-full rounded border border-default px-2 py-1"
            />
          </label>
          <label class="text-sm text-muted">
            Standard-Gate-Severity
            <select
              [(ngModel)]="failOn"
              name="failOn"
              title="Mindest-Severity, ab der das Gate rot wird, wenn keine Policy greift"
              class="mt-1 rounded border border-default px-2 py-1"
            >
              @for (sev of severities; track sev) {
                <option [ngValue]="sev">{{ sev }}</option>
              }
            </select>
          </label>
          <label class="text-sm text-muted">
            Standard-Scan-Modus
            <select
              [(ngModel)]="scanMode"
              name="scanMode"
              title="full = gesamte Historie; incremental = nur neue Commits"
              class="mt-1 rounded border border-default px-2 py-1"
            >
              <option value="full">full</option>
              <option value="incremental">incremental</option>
            </select>
          </label>
          <label class="text-sm text-muted">
            Aufbewahrung (Tage)
            <input
              type="number"
              [(ngModel)]="retentionDays"
              name="retentionDays"
              title="Aufbewahrungsfrist für Scan-Ergebnisse in Tagen, z. B. 365"
              class="mt-1 w-32 rounded border border-default px-2 py-1"
            />
          </label>
          <div>
            <button
              type="submit"
              class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
            >
              Speichern
            </button>
            @if (saved()) {
              <span class="ml-3 text-sm text-muted">gespeichert ✓</span>
            }
          </div>
        </form>

        <h3 class="mb-2 font-medium text-fg">Secret-Referenzen</h3>
        <table class="w-full max-w-xl text-sm">
          <thead>
            <tr class="border-b border-default text-left text-muted">
              <th class="py-2">Referenz</th>
              <th>Auflösbar</th>
            </tr>
          </thead>
          <tbody>
            @for (r of s.secretRefs; track r.ref) {
              <tr class="border-b border-default">
                <td class="py-2 font-mono text-xs">{{ r.ref }}</td>
                <td [style.color]="r.resolvable ? 'var(--color-sev-low)' : 'var(--color-sev-high)'">
                  {{ r.resolvable ? 'ja' : 'nein' }}
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="2" class="py-3 text-muted">Keine Secret-Referenzen hinterlegt.</td>
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

  protected readonly severities: Severity[] = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly settings = signal<Settings | null>(null);
  protected readonly saved = signal(false);

  protected email = '';
  protected failOn: Severity = 'HIGH';
  protected scanMode = 'full';
  protected retentionDays = 365;

  constructor() {
    this.reload();
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
