import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SettingsPage } from './settings-page';

describe('SettingsPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt Einstellungen und Secrets beim Start', async () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/settings');
    expect(req.request.method).toBe('GET');
    req.flush({
      generalNotificationEmail: null,
      defaultFailOn: 'HIGH',
      defaultScanMode: 'full',
      retentionDays: 365,
      secretRefs: [],
    });
    httpMock.expectOne('/api/secrets').flush([]);
  });

  it('legt ein DB-verschlüsseltes Secret an und sendet Klartext nur als Eingabe', () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    httpMock.expectOne('/api/settings').flush({
      generalNotificationEmail: null,
      defaultFailOn: 'HIGH',
      defaultScanMode: 'full',
      retentionDays: 365,
      secretRefs: [],
    });
    httpMock.expectOne('/api/secrets').flush([]);

    const component = fixture.componentInstance as unknown as {
      secretName: string;
      secretMode: string;
      secretValue: string;
      saveSecret: () => void;
    };
    component.secretName = 'crm-token';
    component.secretMode = 'DB_ENCRYPTED';
    component.secretValue = 'super-secret';
    component.saveSecret();

    const req = httpMock.expectOne('/api/secrets');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.mode).toBe('DB_ENCRYPTED');
    expect(req.request.body.plaintext).toBe('super-secret');
    // Server-Antwort trägt nie Klartext zurück:
    req.flush({
      id: 's1',
      name: 'crm-token',
      mode: 'DB_ENCRYPTED',
      reference: '',
      hasStoredValue: true,
      resolvable: true,
    });
    httpMock.expectOne('/api/secrets').flush([]);
  });
});
