import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RepositoriesPage } from './repositories-page';
import { RepositoryCard, RepositorySource } from '../../core/models/scanner';

function source(remediationEnabled: boolean): RepositorySource {
  return {
    id: 'r1',
    name: 'repo-x',
    type: 'github',
    location: 'https://github.com/o/r.git',
    branches: [],
    tokenRef: 'env:GH',
    enabled: true,
    reportEmails: [],
    remediationEnabled,
    description: '',
    visibility: 'private',
  };
}

function card(): RepositoryCard {
  return {
    id: 'r1',
    name: 'repo-x',
    type: 'github',
    visibility: 'public',
    description: 'demo',
    enabled: true,
    language: 'Java',
    lastScanAt: null,
    lastStatus: null,
    lastError: null,
  };
}

describe('RepositoriesPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepositoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt beim Start die Karten-Übersicht (serverseitige Query)', () => {
    const fixture = TestBed.createComponent(RepositoriesPage);
    fixture.detectChanges();
    const cardsReq = httpMock.expectOne((r) => r.url === '/api/sources/cards');
    expect(cardsReq.request.method).toBe('GET');
    cardsReq.flush([card()]);
    httpMock.expectOne('/api/sources').flush([source(false)]);

    const component = fixture.componentInstance as unknown as { cards: () => RepositoryCard[] };
    expect(component.cards().length).toBe(1);
  });

  it('zeigt den Fehler eines fehlgeschlagenen Scans auf der Karte', () => {
    const fixture = TestBed.createComponent(RepositoriesPage);
    fixture.detectChanges();
    const failed: RepositoryCard = {
      ...card(),
      lastStatus: 'FAILED',
      lastError: 'local path does not exist: https://github.com/x/y',
    };
    httpMock.expectOne((r) => r.url === '/api/sources/cards').flush([failed]);
    httpMock.expectOne('/api/sources').flush([source(false)]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('local path does not exist');
  });

  it('schaltet das Repo-Remediation-Opt-in um', () => {
    const fixture = TestBed.createComponent(RepositoriesPage);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/sources/cards').flush([]);
    httpMock.expectOne('/api/sources').flush([source(false)]);

    const component = fixture.componentInstance as unknown as {
      toggleRemediation: (s: RepositorySource) => void;
    };
    component.toggleRemediation(source(false));

    const req = httpMock.expectOne('/api/sources');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.remediationEnabled).toBe(true);
    req.flush(source(true));
    httpMock.expectOne('/api/sources').flush([source(true)]);
  });

  it('holt eine redigierte Scrub-Vorschau', () => {
    const fixture = TestBed.createComponent(RepositoriesPage);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/sources/cards').flush([]);
    httpMock.expectOne('/api/sources').flush([source(true)]);

    const component = fixture.componentInstance as unknown as {
      scrubDryRun: (s: RepositorySource) => void;
      message: () => string;
    };
    component.scrubDryRun(source(true));

    const req = httpMock.expectOne('/api/repos/r1/scrub/dry-run');
    expect(req.request.method).toBe('POST');
    req.flush({ toolAvailable: false, affectedSecrets: 1, diffSummary: 'VORSCHAU …' });

    expect(component.message()).toContain('Secret');
  });
});
