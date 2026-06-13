import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DataSource,
  DataSourceSchema,
  DetectorInfo,
  Finding,
  Policy,
  PrRef,
  RepositorySource,
  Scan,
  ScrubDryRun,
  ScrubResult,
  Settings,
  Severity,
  TriageStatus,
} from '../models/scanner';

/** Einziger Zugang der UI zum Backend (BFF, WR-20): typisierte REST-Aufrufe auf /api/*. */
@Injectable({ providedIn: 'root' })
export class ScannerApi {
  private readonly http = inject(HttpClient);

  recentScans(limit = 50): Observable<Scan[]> {
    return this.http.get<Scan[]>('/api/scans', { params: new HttpParams().set('limit', limit) });
  }

  startScan(sourceId: string, mode: string): Observable<Scan> {
    return this.http.post<Scan>('/api/scans', { sourceId, mode });
  }

  cancelScan(id: string): Observable<void> {
    return this.http.post<void>(`/api/scans/${id}/cancel`, {});
  }

  findings(filter: {
    repo?: string;
    minSeverity?: Severity;
    detector?: string;
    status?: TriageStatus;
  }): Observable<Finding[]> {
    let params = new HttpParams();
    if (filter.repo) params = params.set('repo', filter.repo);
    if (filter.minSeverity) params = params.set('minSeverity', filter.minSeverity);
    if (filter.detector) params = params.set('detector', filter.detector);
    if (filter.status) params = params.set('status', filter.status);
    return this.http.get<Finding[]>('/api/findings', { params });
  }

  triage(id: string, status: TriageStatus, reason?: string): Observable<Finding> {
    return this.http.post<Finding>(`/api/findings/${id}/triage`, { status, reason });
  }

  sources(): Observable<RepositorySource[]> {
    return this.http.get<RepositorySource[]>('/api/sources');
  }

  createSource(source: RepositorySource): Observable<RepositorySource> {
    return this.http.post<RepositorySource>('/api/sources', source);
  }

  deleteSource(id: string): Observable<void> {
    return this.http.delete<void>(`/api/sources/${id}`);
  }

  testConnection(id: string): Observable<{ reachable: boolean }> {
    return this.http.post<{ reachable: boolean }>(`/api/sources/${id}/test`, {});
  }

  detectors(): Observable<DetectorInfo[]> {
    return this.http.get<DetectorInfo[]>('/api/detectors');
  }

  policies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/policies');
  }

  createPolicy(policy: Policy): Observable<Policy> {
    return this.http.post<Policy>('/api/policies', policy);
  }

  deletePolicy(id: string): Observable<void> {
    return this.http.delete<void>(`/api/policies/${id}`);
  }

  // --- Remediation (Phase 6, RMR-*) ---

  /** Auto-Fix per PR/MR für einen Fund (Operator+, RMR-10). */
  remediate(findingId: string): Observable<PrRef> {
    return this.http.post<PrRef>(`/api/findings/${findingId}/remediate`, {});
  }

  /** Pflicht-Vorschau der History-Bereinigung (Operator+, RMR-22). */
  scrubDryRun(repoId: string): Observable<ScrubDryRun> {
    return this.http.post<ScrubDryRun>(`/api/repos/${repoId}/scrub/dry-run`, {});
  }

  /** Realer Scrub-Lauf (Admin/Break-Glass, RMR-25/41); verlangt Freigaben. */
  scrubExecute(
    repoId: string,
    forcePushApproved: boolean,
    rotationConfirmed: boolean,
  ): Observable<ScrubResult> {
    return this.http.post<ScrubResult>(`/api/repos/${repoId}/scrub/execute`, {
      forcePushApproved,
      rotationConfirmed,
    });
  }

  // --- Externe Datenquellen (Phase 7, FR-21/22) ---

  dataSources(): Observable<DataSource[]> {
    return this.http.get<DataSource[]>('/api/datasources');
  }

  saveDataSource(source: DataSource): Observable<DataSource> {
    return this.http.post<DataSource>('/api/datasources', source);
  }

  deleteDataSource(id: string): Observable<void> {
    return this.http.delete<void>(`/api/datasources/${id}`);
  }

  /** Probe-Abruf: redigiertes Attribut-Schema für das Mapping (IR-63, WR-51). */
  probeDataSource(source: DataSource): Observable<DataSourceSchema> {
    return this.http.post<DataSourceSchema>('/api/datasources/probe', source);
  }

  /** Lädt eine Key-Value-Liste (CSV/JSON) hoch; Antwort: Anzahl Hashes je Attribut (WR-56). */
  uploadKeyValues(id: string, content: string): Observable<Record<string, number>> {
    return this.http.post<Record<string, number>>(`/api/datasources/${id}/upload`, content, {
      headers: { 'Content-Type': 'text/plain' },
    });
  }

  settings(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  saveSettings(settings: Settings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings', settings);
  }
}
