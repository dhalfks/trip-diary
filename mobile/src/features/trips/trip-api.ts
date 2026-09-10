import { api } from '@/lib/api';
import type { Trip, TripInput, TripPage } from './types';

const jsonRequest = (method: 'POST' | 'PUT', body: TripInput): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
});

export const tripApi = {
  list: (page = 0, size = 20) => api<TripPage>(`/trips?page=${page}&size=${size}`),
  get: (tripId: string) => api<Trip>(`/trips/${encodeURIComponent(tripId)}`),
  create: (input: TripInput) => api<Trip>('/trips', jsonRequest('POST', input)),
  update: (tripId: string, input: TripInput) => api<Trip>(`/trips/${encodeURIComponent(tripId)}`, jsonRequest('PUT', input)),
  remove: (tripId: string) => api<void>(`/trips/${encodeURIComponent(tripId)}`, { method: 'DELETE' }),
};
