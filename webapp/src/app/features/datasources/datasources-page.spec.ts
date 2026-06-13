import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DataSourcesPage } from './datasources-page';

describe('DataSourcesPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataSourcesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt Datenquellen beim Start', () => {
    const fixture = TestBed.createComponent(DataSourcesPage);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/datasources');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('Probe füllt das redigierte Attribut-Mapping', () => {
    const fixture = TestBed.createComponent(DataSourcesPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/datasources').flush([]);

    const component = fixture.componentInstance as unknown as {
      baseUrl: string;
      probeDraft: () => void;
      schema: () => { field: string; maskedExample: string }[];
    };
    component.baseUrl = 'https://crm.intern';
    component.probeDraft();

    const req = httpMock.expectOne('/api/datasources/probe');
    expect(req.request.method).toBe('POST');
    req.flush({
      reachable: true,
      sampleRecords: 1,
      attributes: [{ field: 'partnernummer', maskedExample: '12******' }],
      message: 'OK',
    });

    expect(component.schema().length).toBe(1);
    expect(component.schema()[0].maskedExample).not.toContain('12345678');
  });

  it('lädt eine Key-Value-Datei hoch und meldet die Hash-Anzahl', async () => {
    const fixture = TestBed.createComponent(DataSourcesPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/datasources').flush([]);

    const component = fixture.componentInstance as unknown as {
      onUpload: (s: { id: string | null; kind: string }, e: Event) => void;
      message: () => string;
    };
    const file = new File(['key,value\npartnernummer,12345678'], 'kv.csv', { type: 'text/csv' });
    const input = { files: [file], value: '' } as unknown as HTMLInputElement;
    component.onUpload({ id: 'r1', kind: 'UPLOAD' }, { target: input } as unknown as Event);

    // file.text() ist async — auf den POST warten.
    await new Promise((r) => setTimeout(r, 0));
    const req = httpMock.expectOne('/api/datasources/r1/upload');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toContain('partnernummer');
    req.flush({ partnernummer: 1 });
    httpMock.expectOne('/api/datasources').flush([]);

    expect(component.message()).toContain('Hash');
  });
});
