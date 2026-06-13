import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RepositoriesPage } from './repositories-page';
import { RepositorySource } from '../../core/models/scanner';

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
  };
}

describe('RepositoriesPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepositoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('schaltet das Repo-Remediation-Opt-in um', () => {
    const fixture = TestBed.createComponent(RepositoriesPage);
    fixture.detectChanges();
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
