export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  meta?: { correlationId?: string | null; timestamp?: string };
}

export interface ApiError {
  code: string;
  message: string;
  details?: string[] | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
