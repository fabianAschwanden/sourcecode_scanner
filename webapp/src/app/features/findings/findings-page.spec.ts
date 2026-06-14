import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { FindingsPage } from './findings-page';
import { Finding } from '../../core/models/scanner';

function secretFinding(): Finding {
  return {
    id: 'f1',
    scanId: 's1',
    repoId: 'repo-x',
    detectorId: 'secret.regex-ruleset',
    category: 'SECRET',
    severity: 'HIGH',
    ruleId: 'aws',
    file: 'src/A.java',
    line: 1,
    redactedMatch: 'AKIA****MPLE',
    verified: false,
    triageStatus: 'OPEN',
    triageReason: null,
    remediationStatus: 'OPEN',
    firstSeen: '2026-06-13T00:00:00Z',
    lastSeen: '2026-06-13T00:00:00Z',
  };
}

describe('FindingsPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FindingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt Funde und Detektoren beim Start', () => {
    const fixture = TestBed.createComponent(FindingsPage);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/findings').flush([]);
    httpMock
      .expectOne('/api/detectors')
      .flush([{ id: 'secret.regex-ruleset', category: 'SECRET' }]);
  });

  it('löst einen Auto-Fix-PR aus und zeigt die PR-URL', () => {
    const fixture = TestBed.createComponent(FindingsPage);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/findings').flush([secretFinding()]);
    httpMock.expectOne('/api/detectors').flush([]);

    const component = fixture.componentInstance as unknown as {
      remediate: (f: Finding) => void;
      message: () => string;
    };
    component.remediate(secretFinding());

    const req = httpMock.expectOne('/api/findings/f1/remediate');
    expect(req.request.method).toBe('POST');
    req.flush({ url: 'https://github.com/o/r/pull/7', number: 7 });

    expect(component.message()).toContain('#7');
  });

  it('Status-Tabs filtern offen vs. geschlossen', () => {
    const fixture = TestBed.createComponent(FindingsPage);
    fixture.detectChanges();
    const closed: Finding = { ...secretFinding(), id: 'f2', triageStatus: 'BASELINE' };
    httpMock.expectOne((r) => r.url === '/api/findings').flush([secretFinding(), closed]);
    httpMock.expectOne('/api/detectors').flush([]);

    const component = fixture.componentInstance as unknown as {
      openCount: () => number;
      closedCount: () => number;
      setStatusTab: (t: 'open' | 'closed') => void;
      visibleFindings: () => Finding[];
    };
    expect(component.openCount()).toBe(1);
    expect(component.closedCount()).toBe(1);
    component.setStatusTab('closed');
    expect(component.visibleFindings().map((f) => f.id)).toEqual(['f2']);
  });
});
