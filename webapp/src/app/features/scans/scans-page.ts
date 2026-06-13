import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { RepositorySource, Scan } from '../../core/models/scanner';

/** Scan-Steuerung (WR-03): starten/abbrechen + Verlauf. Live-Status via Polling (SSE-Endpoint vorhanden). */
@Component({
  selector: 'app-scans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Scans</h2>

      <div class="mb-4 flex items-end gap-2">
        <label class="text-sm">
          Quelle
          <select [(ngModel)]="selectedSource" class="ml-2 rounded border px-2 py-1">
            @for (s of sources(); track s.id) {
              <option [ngValue]="s.id">{{ s.name }}</option>
            }
          </select>
        </label>
        <label class="text-sm">
          Modus
          <select [(ngModel)]="mode" class="ml-2 rounded border px-2 py-1">
            <option value="full">full</option>
            <option value="incremental">incremental</option>
          </select>
        </label>
        <button
          (click)="start()"
          [disabled]="!selectedSource"
          class="rounded bg-blue-600 px-3 py-1 text-white disabled:opacity-50"
        >
          Scan starten
        </button>
      </div>

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b text-left text-gray-500">
            <th class="py-2">Repository</th>
            <th>Modus</th>
            <th>Status</th>
            <th>Fortschritt</th>
            <th>Funde</th>
            <th>Aktionen</th>
          </tr>
        </thead>
        <tbody>
          @for (s of scans(); track s.id) {
            <tr class="border-b">
              <td class="py-2">{{ s.repoId }}</td>
              <td>{{ s.mode }}</td>
              <td>{{ s.status }}</td>
              <td>{{ s.progress }}%</td>
              <td>{{ s.findingCount }}</td>
              <td>
                @if (s.status === 'RUNNING') {
                  <button (click)="cancel(s)" class="text-red-600 hover:underline">
                    Abbrechen
                  </button>
                }
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" class="py-3 text-gray-500">Noch keine Scans.</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class ScansPage {
  private readonly api = inject(ScannerApi);

  protected readonly sources = signal<RepositorySource[]>([]);
  protected readonly scans = signal<Scan[]>([]);
  protected selectedSource: string | null = null;
  protected mode = 'full';

  constructor() {
    this.api.sources().subscribe((list) => {
      this.sources.set(list);
      this.selectedSource = list[0]?.id ?? null;
    });
    this.reload();
  }

  protected start(): void {
    if (!this.selectedSource) {
      return;
    }
    this.api.startScan(this.selectedSource, this.mode).subscribe(() => this.reload());
  }

  protected cancel(scan: Scan): void {
    this.api.cancelScan(scan.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.recentScans(50).subscribe((list) => this.scans.set(list));
  }
}
