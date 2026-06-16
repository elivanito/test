import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  PotentialSupplier,
  SustainabilityRating,
} from '../../core/api.types';
import { PotentialSuppliersService } from '../../core/potential-suppliers.service';

type SortColumn = 'duns' | 'name' | 'country' | 'annualTurnover' | 'sustainabilityRating' | 'score';
type SortDirection = 'asc' | 'desc';

const ALL_RATINGS: SustainabilityRating[] = ['A', 'B', 'C', 'D', 'E'];
const MIN_RATE = 250;
const DEFAULT_LIMIT = 10;

@Component({
  selector: 'app-potential-suppliers',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
  templateUrl: './potential-suppliers.component.html',
  styleUrls: ['./potential-suppliers.component.css'],
})
export class PotentialSuppliersComponent {
  private readonly api = inject(PotentialSuppliersService);

  // -------- Inputs / state --------
  rateInput = signal<number | null>(null);
  readonly minRate = MIN_RATE;

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Last successfully applied rate (used as identity for the loaded page). */
  readonly appliedRate = signal<number | null>(null);

  // Pagination
  readonly limit = signal<number>(DEFAULT_LIMIT);
  readonly offset = signal<number>(0);
  readonly total = signal<number>(0);

  // Raw page from backend (server-sorted by score desc by default)
  readonly suppliers = signal<PotentialSupplier[]>([]);

  // Client filters / search / sorting (applied on top of the current page)
  readonly search = signal<string>('');
  readonly countryFilter = signal<string>(''); // '' = all
  readonly ratingFilter = signal<SustainabilityRating | ''>('');
  readonly sortColumn = signal<SortColumn>('score');
  readonly sortDirection = signal<SortDirection>('desc');

  readonly allRatings = ALL_RATINGS;

  // -------- Derived --------
  readonly availableCountries = computed(() => {
    const set = new Set(this.suppliers().map((s) => s.country));
    return Array.from(set).sort();
  });

  readonly filteredSuppliers = computed<PotentialSupplier[]>(() => {
    const q = this.search().trim().toLowerCase();
    const c = this.countryFilter();
    const r = this.ratingFilter();

    let rows = this.suppliers();

    if (q) {
      rows = rows.filter(
        (s) =>
          s.name.toLowerCase().includes(q) || s.duns.toString().includes(q),
      );
    }
    if (c) rows = rows.filter((s) => s.country === c);
    if (r) rows = rows.filter((s) => s.sustainabilityRating === r);

    const col = this.sortColumn();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;
    rows = [...rows].sort((a, b) => {
      const av = a[col];
      const bv = b[col];
      if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir;
      return String(av).localeCompare(String(bv)) * dir;
    });

    return rows;
  });

  readonly hasNextPage = computed(() => this.offset() + this.limit() < this.total());
  readonly hasPrevPage = computed(() => this.offset() > 0);
  readonly currentPage = computed(() => Math.floor(this.offset() / this.limit()) + 1);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.limit())));

  readonly rateInvalid = computed(() => {
    const v = this.rateInput();
    return v !== null && v < MIN_RATE;
  });

  // -------- Actions --------
  search$(reset = true): void {
    const v = this.rateInput();
    if (v === null || v < MIN_RATE) return;
    if (reset) this.offset.set(0);
    this.fetchPage(v);
  }

  goToPage(direction: 1 | -1): void {
    const next = this.offset() + direction * this.limit();
    if (next < 0) return;
    this.offset.set(next);
    const v = this.appliedRate();
    if (v !== null) this.fetchPage(v);
  }

  changeLimit(value: number): void {
    this.limit.set(value);
    this.offset.set(0);
    const v = this.appliedRate();
    if (v !== null) this.fetchPage(v);
  }

  toggleSort(column: SortColumn): void {
    if (this.sortColumn() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set(column === 'score' ? 'desc' : 'asc');
    }
  }

  sortGlyph(column: SortColumn): string {
    if (this.sortColumn() !== column) return '';
    return this.sortDirection() === 'asc' ? ' \u25B2' : ' \u25BC';
  }

  clearFilters(): void {
    this.search.set('');
    this.countryFilter.set('');
    this.ratingFilter.set('');
  }

  // -------- Private --------
  private fetchPage(rate: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.findPotential({ rate, limit: this.limit(), offset: this.offset() }).subscribe({
      next: (resp) => {
        this.suppliers.set(resp.data);
        this.total.set(resp.pagination.total);
        this.appliedRate.set(rate);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.errorMessage.set(err.message || 'Unexpected error');
        this.suppliers.set([]);
        this.total.set(0);
        this.loading.set(false);
      },
    });
  }
}
