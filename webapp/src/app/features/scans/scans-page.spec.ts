import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ScansPage } from './scans-page';
import { Scan, ScanEvent } from '../../core/models/scanner';

function runningScan(progress: number): Scan {
  return {
    id: 'scan-1',
    repoId: 'repo-x',
    mode: 'full',
    status: 'RUNNING',
    progress,
    findingCount: 0,
    startedAt: '2026-06-14T00:00:00Z',
    finishedAt: null,
    trigger: 'SERVER',
    ciPipelineUrl: null,
    ciCommit: null,
    ciBranch: null,
    ciActor: null,
  };
}

describe('ScansPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScansPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt Quellen und Scans beim Start', () => {
    const fixture = TestBed.createComponent(ScansPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/sources').flush([]);
    httpMock.expectOne((r) => r.url === '/api/scans').flush([]);
  });

  it('zeigt den persistierten Fortschritt als Fallback', () => {
    const fixture = TestBed.createComponent(ScansPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/sources').flush([]);
    httpMock.expectOne((r) => r.url === '/api/scans').flush([runningScan(42)]);

    const component = fixture.componentInstance as unknown as {
      livePercent: (s: Scan) => number;
    };
    expect(component.livePercent(runningScan(42))).toBe(42);
  });

  it('Live-Event überschreibt den persistierten Fortschritt (WR-04a)', () => {
    const fixture = TestBed.createComponent(ScansPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/sources').flush([]);
    httpMock.expectOne((r) => r.url === '/api/scans').flush([runningScan(10)]);

    const component = fixture.componentInstance as unknown as {
      live: { update: (fn: (m: Record<string, ScanEvent>) => Record<string, ScanEvent>) => void };
      livePercent: (s: Scan) => number;
    };
    component.live.update((m) => ({
      ...m,
      'scan-1': { scanId: 'scan-1', status: 'RUNNING', progress: 80, findingCount: 3 },
    }));
    expect(component.livePercent(runningScan(10))).toBe(80);
  });
});
