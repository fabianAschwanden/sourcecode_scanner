import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Component } from '@angular/core';
import { App } from './app';

@Component({ selector: 'app-stub', template: '' })
class StubPage {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: 'dashboard', component: StubPage }])],
    }).compileComponents();
  });

  it('zeigt die App-Shell (Header mit Titel) auf einer App-Seite', async () => {
    await TestBed.inject(Router).navigate(['/dashboard']);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('sourcecode-scanner');
  });
});
