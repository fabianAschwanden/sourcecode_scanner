import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import {
  AttributeCategory,
  AttributeRule,
  DataSource,
  DataSourceAuthType,
  Severity,
} from '../../core/models/scanner';

/**
 * Externe Datenquellen für Kundendaten-Erkennung verwalten (WR-50..54): anlegen, testweise abrufen
 * (redigiertes Attribut-Schema) und je Attribut das Mapping pflegen (prüfen/Severity/Kategorie).
 * Werte werden nie angezeigt — nur Feldnamen + maskierte Beispiele (WR-54). GitHub-Dark + Tooltips.
 */
@Component({
  selector: 'app-datasources-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Externe Datenquellen</h2>
      <p class="mb-4 text-sm text-muted">
        REST-API mit vertraulichen Kundendaten (z. B. Partnernummern). Die geprüften Attribute
        werden im Code gesucht. Werte verlassen den Server nie unredigiert; Token nur als Referenz.
      </p>

      <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
        <input
          [(ngModel)]="name"
          name="name"
          placeholder="Name"
          title="Eindeutiger Name der Datenquelle, z. B. crm-partners"
          required
          class="rounded border border-default px-2 py-1"
        />
        <input
          [(ngModel)]="baseUrl"
          name="baseUrl"
          placeholder="Basis-URL"
          title="Basis-URL der REST-API, z. B. https://crm.intern/api/v1"
          required
          class="w-64 rounded border border-default px-2 py-1"
        />
        <input
          [(ngModel)]="path"
          name="path"
          placeholder="Pfad"
          title="Relativer Pfad zur Datenliste, z. B. /partners"
          class="rounded border border-default px-2 py-1"
        />
        <input
          [(ngModel)]="recordsPath"
          name="recordsPath"
          placeholder="Datensatz-Pfad"
          title="JSONPath auf die Datensätze, z. B. $.data[*] oder $[*]"
          class="rounded border border-default px-2 py-1"
        />
        <select
          [(ngModel)]="authType"
          name="authType"
          title="Authentifizierung gegen die API"
          class="rounded border border-default px-2 py-1"
        >
          <option value="NONE">Keine Auth</option>
          <option value="BEARER">Bearer</option>
          <option value="BASIC">Basic</option>
          <option value="HEADER">Header</option>
        </select>
        <input
          [(ngModel)]="tokenRef"
          name="tokenRef"
          placeholder="tokenRef (env:NAME)"
          title="Secret-Referenz, kein Klartext — z. B. env:CRM_API_TOKEN"
          class="rounded border border-default px-2 py-1"
        />
        <button
          type="submit"
          class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
        >
          Anlegen
        </button>
        <button
          type="button"
          (click)="probeDraft()"
          title="Ruft die Datenquelle testweise ab und zeigt die verfügbaren Attribute (redigiert)."
          class="rounded border border-default px-3 py-1 hover:underline"
        >
          Attribute abrufen
        </button>
      </form>

      @if (schema().length > 0) {
        <div class="mb-6 rounded border border-default p-3">
          <h3 class="mb-2 text-sm font-semibold">
            Attribut-Mapping (redigierte Beispiele) — wählen, was im Code geprüft wird
          </h3>
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-default text-left text-muted">
                <th class="py-1">Attribut</th>
                <th>Beispiel (maskiert)</th>
                <th>Prüfen</th>
                <th>Severity</th>
                <th>Kategorie</th>
              </tr>
            </thead>
            <tbody>
              @for (a of schema(); track a.field) {
                <tr class="border-b border-default">
                  <td class="py-1 font-mono text-xs">{{ a.field }}</td>
                  <td class="font-mono text-xs text-muted">{{ a.maskedExample }}</td>
                  <td><input type="checkbox" [(ngModel)]="draftMap[a.field].check" /></td>
                  <td>
                    <select
                      [(ngModel)]="draftMap[a.field].severity"
                      class="rounded border border-default px-1"
                    >
                      @for (s of severities; track s) {
                        <option [value]="s">{{ s }}</option>
                      }
                    </select>
                  </td>
                  <td>
                    <select
                      [(ngModel)]="draftMap[a.field].category"
                      class="rounded border border-default px-1"
                    >
                      <option value="PII">PII</option>
                      <option value="CUSTOM">CUSTOM</option>
                    </select>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <button
            (click)="saveDraftWithMapping()"
            class="mt-2 rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
          >
            Datenquelle mit Mapping speichern
          </button>
        </div>
      }

      @if (message()) {
        <p class="mb-4 rounded border border-default px-3 py-2 text-sm text-muted">
          {{ message() }}
        </p>
      }

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-default text-left text-muted">
            <th class="py-2">Name</th>
            <th>Basis-URL</th>
            <th>Auth</th>
            <th>Aktiv</th>
            <th>Geprüfte Attribute</th>
            <th>Aktionen</th>
          </tr>
        </thead>
        <tbody>
          @for (s of sources(); track s.id) {
            <tr class="border-b border-default">
              <td class="py-2">{{ s.name }}</td>
              <td class="font-mono text-xs">{{ s.baseUrl }}{{ s.path }}</td>
              <td>{{ s.authType }}</td>
              <td>{{ s.enabled ? 'ja' : 'nein' }}</td>
              <td class="text-xs">{{ checkedFields(s) || '—' }}</td>
              <td class="space-x-2">
                <button (click)="remove(s)" class="text-sev-high hover:underline">Löschen</button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="py-3 text-muted">Keine Datenquellen.</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class DataSourcesPage {
  private readonly api = inject(ScannerApi);

  protected readonly severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
  protected readonly sources = signal<DataSource[]>([]);
  protected readonly schema = signal<{ field: string; maskedExample: string }[]>([]);
  protected readonly message = signal<string>('');

  protected name = '';
  protected baseUrl = '';
  protected path = '';
  protected recordsPath = '$[*]';
  protected authType: DataSourceAuthType = 'NONE';
  protected tokenRef = '';
  protected draftMap: Record<
    string,
    { check: boolean; severity: Severity; category: AttributeCategory }
  > = {};

  constructor() {
    this.reload();
  }

  protected probeDraft(): void {
    this.message.set('Rufe Datenquelle ab …');
    this.api.probeDataSource(this.draft([])).subscribe({
      next: (s) => {
        if (!s.reachable) {
          this.message.set(`Nicht erreichbar: ${s.message}`);
          this.schema.set([]);
          return;
        }
        this.draftMap = {};
        for (const a of s.attributes) {
          this.draftMap[a.field] = { check: false, severity: 'MEDIUM', category: 'PII' };
        }
        this.schema.set(s.attributes);
        this.message.set(
          `${s.sampleRecords} Datensatz/Datensätze, ${s.attributes.length} Attribut(e).`,
        );
      },
      error: (err) => this.message.set(`Abruf fehlgeschlagen: ${err?.error?.error ?? 'Fehler'}`),
    });
  }

  protected saveDraftWithMapping(): void {
    const attributes: AttributeRule[] = this.schema().map((a) => ({
      field: a.field,
      check: this.draftMap[a.field].check,
      severity: this.draftMap[a.field].severity,
      category: this.draftMap[a.field].category,
    }));
    this.api.saveDataSource(this.draft(attributes)).subscribe(() => {
      this.message.set('Datenquelle gespeichert.');
      this.schema.set([]);
      this.resetForm();
      this.reload();
    });
  }

  protected create(): void {
    if (!this.name || !this.baseUrl) {
      return;
    }
    this.api.saveDataSource(this.draft([])).subscribe(() => {
      this.resetForm();
      this.reload();
    });
  }

  protected remove(source: DataSource): void {
    if (!source.id) {
      return;
    }
    this.api.deleteDataSource(source.id).subscribe(() => this.reload());
  }

  protected checkedFields(source: DataSource): string {
    return source.attributes
      .filter((a) => a.check)
      .map((a) => a.field)
      .join(', ');
  }

  private draft(attributes: AttributeRule[]): DataSource {
    return {
      id: null,
      name: this.name,
      baseUrl: this.baseUrl,
      method: 'GET',
      path: this.path,
      authType: this.authType,
      tokenRef: this.tokenRef || null,
      authHeaderName: null,
      recordsPath: this.recordsPath,
      cacheTtlSeconds: 600,
      minValueLength: 4,
      enabled: true,
      attributes,
    };
  }

  private resetForm(): void {
    this.name = '';
    this.baseUrl = '';
    this.path = '';
    this.recordsPath = '$[*]';
    this.authType = 'NONE';
    this.tokenRef = '';
    this.draftMap = {};
  }

  private reload(): void {
    this.api.dataSources().subscribe((list) => this.sources.set(list));
  }
}
