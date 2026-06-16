import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('renders the application title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Inditex');
    expect(text).toContain('Potential Suppliers');
  });

  it('embeds the <app-potential-suppliers /> child component', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      'app-potential-suppliers',
    );
    expect(el).not.toBeNull();
  });
});
