import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DetectorInfo,
  Finding,
  Policy,
  RepositorySource,
  Scan,
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

  settings(): Observable<Settings> {
    return this.http.get<Settings>('/api/settings');
  }

  saveSettings(settings: Settings): Observable<Settings> {
    return this.http.put<Settings>('/api/settings', settings);
  }
}
