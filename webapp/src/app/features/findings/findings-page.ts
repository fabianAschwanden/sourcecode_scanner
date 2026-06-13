import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { Finding, Severity, TriageStatus } from '../../core/models/scanner';

/** Finding-Liste mit Filter + Triage (WR-10/11/12). */
@Component({
  selector: 'app-findings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Findings</h2>

      <div class="mb-4 flex gap-2">
        <select
          [(ngModel)]="severityFilter"
          (ngModelChange)="reload()"
          class="rounded border px-2 py-1"
        >
          <option [ngValue]="undefined">Alle Severities</option>
          @for (s of severities; track s) {
            <option [ngValue]="s">{{ s }}</option>
          }
        </select>
        <select
          [(ngModel)]="statusFilter"
          (ngModelChange)="reload()"
          class="rounded border px-2 py-1"
        >
          <option [ngValue]="undefined">Alle Status</option>
          @for (s of statuses; track s) {
            <option [ngValue]="s">{{ s }}</option>
          }
        </select>
      </div>

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b text-left text-gray-500">
            <th class="py-2">Severity</th>
            <th>Detektor</th>
            <th>Datei</th>
            <th>Zeile</th>
            <th>Treffer (redigiert)</th>
            <th>Status</th>
            <th>Aktionen</th>
          </tr>
        </thead>
        <tbody>
          @for (f of findings(); track f.id) {
            <tr class="border-b">
              <td class="py-2 font-medium">{{ f.severity }}</td>
              <td>{{ f.detectorId }}</td>
              <td class="font-mono text-xs">{{ f.file }}</td>
              <td>{{ f.line }}</td>
              <td class="font-mono text-xs">{{ f.redactedMatch }}</td>
              <td>{{ f.triageStatus }}</td>
              <td class="space-x-2">
                <button (click)="triage(f, 'BASELINE')" class="text-blue-600 hover:underline">
                  Baseline
                </button>
                <button (click)="triage(f, 'FALSE_POSITIVE')" class="text-blue-600 hover:underline">
                  FP
                </button>
                <button (click)="triage(f, 'SUPPRESSED')" class="text-blue-600 hover:underline">
                  Unterdrücken
                </button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="7" class="py-3 text-gray-500">Keine Funde.</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class FindingsPage {
  private readonly api = inject(ScannerApi);

  protected readonly severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
  protected readonly statuses: TriageStatus[] = [
    'OPEN',
    'BASELINE',
    'SUPPRESSED',
    'FALSE_POSITIVE',
  ];

  protected severityFilter: Severity | undefined;
  protected statusFilter: TriageStatus | undefined;
  protected readonly findings = signal<Finding[]>([]);

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.api
      .findings({ minSeverity: this.severityFilter, status: this.statusFilter })
      .subscribe((list) => this.findings.set(list));
  }

  protected triage(finding: Finding, status: TriageStatus): void {
    const needsReason = status === 'SUPPRESSED' || status === 'FALSE_POSITIVE';
    const reason = needsReason ? (prompt('Begründung (Pflicht):') ?? '') : undefined;
    if (needsReason && !reason) {
      return;
    }
    this.api.triage(finding.id, status, reason).subscribe(() => this.reload());
  }
}
