import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { PotentialSuppliersComponent } from './potential-suppliers.component';
import {
  FindPotentialQuery,
  PotentialSuppliersService,
} from '../../core/potential-suppliers.service';
import {
  PotentialSupplier,
  PotentialSuppliersResponse,
} from '../../core/api.types';

/** Tiny fake — keeps the spec free from HTTP plumbing concerns. */
class FakeApi {
  lastQuery?: FindPotentialQuery;
  response$: (q: FindPotentialQuery) => Observable<PotentialSuppliersResponse> =
    () => of(emptyPage());

  findPotential(q: FindPotentialQuery): Observable<PotentialSuppliersResponse> {
    this.lastQuery = q;
    return this.response$(q);
  }
}

function supplier(over: Partial<PotentialSupplier> = {}): PotentialSupplier {
  return {
    duns: 100_000_001,
    name: 'Zippers Co',
    country: 'ES',
    annualTurnover: 2_000_000,
    sustainabilityRating: 'A',
    status: 'Active',
    score: 250_000,
    ...over,
  };
}

function emptyPage(): PotentialSuppliersResponse {
  return { data: [], pagination: { total: 0, limit: 10, offset: 0 } };
}

function page(
  data: PotentialSupplier[],
  total: number,
  offset = 0,
  limit = 10,
): PotentialSuppliersResponse {
  return { data, pagination: { total, limit, offset } };
}

describe('PotentialSuppliersComponent', () => {
  let fixture: ComponentFixture<PotentialSuppliersComponent>;
  let component: PotentialSuppliersComponent;
  let api: FakeApi;

  beforeEach(async () => {
    api = new FakeApi();
    await TestBed.configureTestingModule({
      imports: [PotentialSuppliersComponent],
      providers: [{ provide: PotentialSuppliersService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(PotentialSuppliersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ---------- Validation: minimum rate ----------

  it('flags the rate as invalid when below the minimum (250)', () => {
    component.rateInput.set(100);
    expect(component.rateInvalid()).toBeTrue();
  });

  it('does not flag the rate when null (initial empty state)', () => {
    expect(component.rateInvalid()).toBeFalse();
  });

  it('refuses to search when no rate has been entered', () => {
    component.search$();
    expect(api.lastQuery).toBeUndefined();
  });

  it('refuses to search when rate is below the minimum', () => {
    component.rateInput.set(50);
    component.search$();
    expect(api.lastQuery).toBeUndefined();
  });

  // ---------- Happy path ----------

  it('loads a page and updates totals + appliedRate', () => {
    const rows = [supplier(), supplier({ duns: 100_000_002, name: 'Buttons SA' })];
    api.response$ = () => of(page(rows, 42));

    component.rateInput.set(1000);
    component.search$();

    expect(api.lastQuery).toEqual({ rate: 1000, limit: 10, offset: 0 });
    expect(component.suppliers()).toEqual(rows);
    expect(component.total()).toBe(42);
    expect(component.appliedRate()).toBe(1000);
    expect(component.loading()).toBeFalse();
    expect(component.errorMessage()).toBeNull();
  });

  // ---------- Filtering ----------

  it('filters by name case-insensitively', () => {
    api.response$ = () =>
      of(
        page(
          [
            supplier({ duns: 1, name: 'Zippers' }),
            supplier({ duns: 2, name: 'Buttons' }),
          ],
          2,
        ),
      );
    component.rateInput.set(1000);
    component.search$();

    component.search.set('zip');
    expect(component.filteredSuppliers().map((s) => s.duns)).toEqual([1]);
  });

  it('filters by DUNS substring', () => {
    api.response$ = () =>
      of(
        page(
          [
            supplier({ duns: 100_000_001 }),
            supplier({ duns: 200_000_002, name: 'Other' }),
          ],
          2,
        ),
      );
    component.rateInput.set(1000);
    component.search$();

    component.search.set('200');
    expect(component.filteredSuppliers().map((s) => s.duns)).toEqual([200_000_002]);
  });

  it('filters by country and by rating independently', () => {
    api.response$ = () =>
      of(
        page(
          [
            supplier({ duns: 1, country: 'ES', sustainabilityRating: 'A' }),
            supplier({ duns: 2, country: 'PT', sustainabilityRating: 'A' }),
            supplier({ duns: 3, country: 'ES', sustainabilityRating: 'C' }),
          ],
          3,
        ),
      );
    component.rateInput.set(1000);
    component.search$();

    component.countryFilter.set('ES');
    expect(component.filteredSuppliers().map((s) => s.duns).sort()).toEqual([1, 3]);

    component.countryFilter.set('');
    component.ratingFilter.set('A');
    expect(component.filteredSuppliers().map((s) => s.duns).sort()).toEqual([1, 2]);
  });

  it('availableCountries returns a sorted unique set from the current page', () => {
    api.response$ = () =>
      of(
        page(
          [
            supplier({ duns: 1, country: 'PT' }),
            supplier({ duns: 2, country: 'ES' }),
            supplier({ duns: 3, country: 'PT' }),
            supplier({ duns: 4, country: 'FR' }),
          ],
          4,
        ),
      );
    component.rateInput.set(1000);
    component.search$();
    expect(component.availableCountries()).toEqual(['ES', 'FR', 'PT']);
  });

  it('clearFilters resets search, country and rating', () => {
    component.search.set('foo');
    component.countryFilter.set('ES');
    component.ratingFilter.set('B');
    component.clearFilters();
    expect(component.search()).toBe('');
    expect(component.countryFilter()).toBe('');
    expect(component.ratingFilter()).toBe('');
  });

  // ---------- Sorting ----------

  it('toggleSort flips the direction for the same column', () => {
    component.toggleSort('name');
    expect(component.sortColumn()).toBe('name');
    expect(component.sortDirection()).toBe('asc');

    component.toggleSort('name');
    expect(component.sortDirection()).toBe('desc');
  });

  it('toggleSort defaults to desc for score, asc otherwise', () => {
    component.toggleSort('duns');
    expect(component.sortDirection()).toBe('asc');

    component.toggleSort('score');
    expect(component.sortDirection()).toBe('desc');
  });

  it('sorts numeric columns numerically', () => {
    api.response$ = () =>
      of(
        page(
          [
            supplier({ duns: 1, score: 100 }),
            supplier({ duns: 2, score: 500 }),
            supplier({ duns: 3, score: 250 }),
          ],
          3,
        ),
      );
    component.rateInput.set(1000);
    component.search$();
    // default is score desc
    expect(component.filteredSuppliers().map((s) => s.score)).toEqual([500, 250, 100]);
  });

  // ---------- Pagination ----------

  it('hasNextPage / hasPrevPage reflect total + offset', () => {
    api.response$ = () => of(page([supplier()], 25, 10, 10));
    component.rateInput.set(1000);
    component.search$();
    component.offset.set(10);

    expect(component.hasPrevPage()).toBeTrue();
    expect(component.hasNextPage()).toBeTrue();
    expect(component.currentPage()).toBe(2);
    expect(component.totalPages()).toBe(3);
  });

  it('goToPage(1) re-fetches with the new offset', () => {
    let calls = 0;
    api.response$ = (q) => {
      calls++;
      return of(page([supplier()], 25, q.offset, q.limit));
    };
    component.rateInput.set(1000);
    component.search$(); // call #1, offset 0
    component.goToPage(1); // call #2, offset 10

    expect(calls).toBe(2);
    expect(api.lastQuery).toEqual({ rate: 1000, limit: 10, offset: 10 });
  });

  it('goToPage(-1) is a no-op when already at offset 0', () => {
    api.response$ = () => of(page([supplier()], 25));
    component.rateInput.set(1000);
    component.search$();
    const before = api.lastQuery;

    component.goToPage(-1);
    // No additional fetch beyond the initial one
    expect(api.lastQuery).toBe(before);
  });

  it('changeLimit resets offset to 0 and refetches', () => {
    api.response$ = (q) => of(page([supplier()], 25, q.offset, q.limit));
    component.rateInput.set(1000);
    component.search$();
    component.offset.set(20);

    component.changeLimit(5);

    expect(component.limit()).toBe(5);
    expect(component.offset()).toBe(0);
    expect(api.lastQuery).toEqual({ rate: 1000, limit: 5, offset: 0 });
  });

  // ---------- Error path ----------

  it('surfaces backend errors into errorMessage and clears the page', () => {
    api.response$ = () => throwError(() => new Error('boom'));
    component.rateInput.set(1000);
    component.search$();

    expect(component.errorMessage()).toBe('boom');
    expect(component.suppliers()).toEqual([]);
    expect(component.total()).toBe(0);
    expect(component.loading()).toBeFalse();
  });

  // ---------- Re-search resets pagination ----------

  it('search$() resets offset to 0 by default', () => {
    api.response$ = (q) => of(page([supplier()], 25, q.offset, q.limit));
    component.rateInput.set(1000);
    component.search$();
    component.offset.set(20);

    component.search$();
    expect(component.offset()).toBe(0);
    expect(api.lastQuery?.offset).toBe(0);
  });
});
