export type SustainabilityRating = 'A' | 'B' | 'C' | 'D' | 'E';
export type SupplierStatus = 'Active' | 'Disqualified';

export interface PotentialSupplier {
  duns: number;
  name: string;
  country: string;
  annualTurnover: number;
  sustainabilityRating: SustainabilityRating;
  status: SupplierStatus;
  score: number;
}

export interface Pagination {
  limit: number;
  offset: number;
  total: number;
  /** Opaque base64 cursor for keyset pagination. Null when no further page exists. */
  nextCursor?: string | null;
}

export interface PotentialSuppliersResponse {
  data: PotentialSupplier[];
  pagination: Pagination;
}

export interface ApiError {
  info: string;
}
