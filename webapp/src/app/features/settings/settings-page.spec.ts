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

  it('lädt die Einstellungen beim Start', async () => {
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
  });
});
