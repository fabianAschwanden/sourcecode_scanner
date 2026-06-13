import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PoliciesPage } from './policies-page';

describe('PoliciesPage', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PoliciesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('lädt die Policies beim Start', async () => {
    const fixture = TestBed.createComponent(PoliciesPage);
    fixture.detectChanges();
    const req = httpMock.expectOne('/api/policies');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
