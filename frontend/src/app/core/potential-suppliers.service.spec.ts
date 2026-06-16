import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { PotentialSuppliersService } from './potential-suppliers.service';
import { PotentialSuppliersResponse } from './api.types';

describe('PotentialSuppliersService', () => {
  let service: PotentialSuppliersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PotentialSuppliersService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(PotentialSuppliersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends rate, limit and offset as query params to /api/v1/suppliers/potential', () => {
    const expected: PotentialSuppliersResponse = {
      data: [],
      pagination: { total: 0, limit: 10, offset: 0 },
    };

    let actual: PotentialSuppliersResponse | undefined;
    service
      .findPotential({ rate: 1000, limit: 10, offset: 0 })
      .subscribe((r) => (actual = r));

    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/suppliers/potential' &&
        r.params.get('rate') === '1000' &&
        r.params.get('limit') === '10' &&
        r.params.get('offset') === '0' &&
        r.params.get('cursor') === null,
    );
    expect(req.request.method).toBe('GET');
    req.flush(expected);

    expect(actual).toEqual(expected);
  });

  it('forwards country and maxRating filters when provided', () => {
    service
      .findPotential({ rate: 1000, limit: 10, offset: 0, country: 'ES', maxRating: 'B' })
      .subscribe();

    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/suppliers/potential' &&
        r.params.get('country') === 'ES' &&
        r.params.get('maxRating') === 'B',
    );
    req.flush({ data: [], pagination: { total: 0, limit: 10, offset: 0 } });
  });

  it('sends cursor (and omits offset) when keyset pagination is requested', () => {
    service
      .findPotential({ rate: 1000, limit: 10, cursor: 'opaque-cursor-token' })
      .subscribe();

    const req = http.expectOne(
      (r) =>
        r.url === '/api/v1/suppliers/potential' &&
        r.params.get('cursor') === 'opaque-cursor-token' &&
        r.params.get('offset') === null,
    );
    req.flush({ data: [], pagination: { limit: 10, offset: 0, nextCursor: null } });
  });

  it('maps an API error payload {info} into a thrown Error', (done) => {
    service.findPotential({ rate: 1000, limit: 10, offset: 0 }).subscribe({
      next: () => done.fail('expected error'),
      error: (err: Error) => {
        expect(err.message).toBe('rate must be >= 250');
        done();
      },
    });

    const req = http.expectOne('/api/v1/suppliers/potential?rate=1000&limit=10&offset=0');
    req.flush(
      { info: 'rate must be >= 250' },
      { status: 400, statusText: 'Bad Request' },
    );
  });

  it('falls back to the HTTP error message when the body is not the API shape', (done) => {
    service.findPotential({ rate: 1000, limit: 10, offset: 0 }).subscribe({
      next: () => done.fail('expected error'),
      error: (err: Error) => {
        expect(err.message).toBeTruthy();
        done();
      },
    });

    const req = http.expectOne('/api/v1/suppliers/potential?rate=1000&limit=10&offset=0');
    req.flush('boom', { status: 500, statusText: 'Server Error' });
  });
});
