import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { ApiError, PotentialSuppliersResponse, SustainabilityRating } from './api.types';

export interface FindPotentialQuery {
  rate: number;
  limit: number;
  /** Used in offset mode (legacy). Ignored when {@code cursor} is provided. */
  offset?: number;
  /** Opaque base64 cursor returned by the server in {@code pagination.nextCursor}.
   *  Passing an empty string explicitly opts into keyset mode on the first page. */
  cursor?: string;
  /** ISO 3166-1 alpha-2 country filter (server-side). */
  country?: string;
  /** Inclusive upper bound on sustainability rating (server-side). */
  maxRating?: SustainabilityRating;
}

@Injectable({ providedIn: 'root' })
export class PotentialSuppliersService {
  private readonly http = inject(HttpClient);
  /** The frontend nginx proxies /api/* to the backend. In dev (ng serve)
   *  this works against http://localhost:8080 via the proxy.conf or direct CORS. */
  private readonly baseUrl = '/api/v1';

  findPotential(q: FindPotentialQuery): Observable<PotentialSuppliersResponse> {
    let params = new HttpParams()
      .set('rate', q.rate.toString())
      .set('limit', q.limit.toString());

    // Offset/keyset are mutually exclusive at the wire level: only one is sent.
    if (q.cursor !== undefined) {
      params = params.set('cursor', q.cursor);
    } else if (q.offset !== undefined) {
      params = params.set('offset', q.offset.toString());
    }
    if (q.country) {
      params = params.set('country', q.country);
    }
    if (q.maxRating) {
      params = params.set('maxRating', q.maxRating);
    }

    return this.http
      .get<PotentialSuppliersResponse>(`${this.baseUrl}/suppliers/potential`, { params })
      .pipe(
        catchError((err: HttpErrorResponse) => {
          const apiErr = (err.error as ApiError | null)?.info ?? err.message ?? 'Unknown error';
          return throwError(() => new Error(apiErr));
        }),
      );
  }
}
