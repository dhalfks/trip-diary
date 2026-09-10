import { api } from '@/lib/api';
import type { DiaryEntry, DiaryEntryInput, Itinerary, ItineraryInput, Place, PlaceInput, Trip, TripInput, TripPage } from './types';

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

function json(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export const placeApi = {
  list: (tripId: string) => api<Place[]>(`/trips/${encodeURIComponent(tripId)}/places`),
  create: (tripId: string, input: PlaceInput) => api<Place>(`/trips/${encodeURIComponent(tripId)}/places`, json('POST', input)),
  update: (tripId: string, placeId: string, input: PlaceInput) => api<Place>(`/trips/${encodeURIComponent(tripId)}/places/${encodeURIComponent(placeId)}`, json('PUT', input)),
  remove: (tripId: string, placeId: string) => api<void>(`/trips/${encodeURIComponent(tripId)}/places/${encodeURIComponent(placeId)}`, { method: 'DELETE' }),
};

const itineraryPath = (tripId: string, dayId: string) => `/trips/${encodeURIComponent(tripId)}/days/${encodeURIComponent(dayId)}/itineraries`;
export const itineraryApi = {
  list: (tripId: string, dayId: string) => api<Itinerary[]>(itineraryPath(tripId, dayId)),
  create: (tripId: string, dayId: string, input: ItineraryInput) => api<Itinerary>(itineraryPath(tripId, dayId), json('POST', input)),
  update: (tripId: string, dayId: string, itemId: string, input: ItineraryInput) => api<Itinerary>(`${itineraryPath(tripId, dayId)}/${encodeURIComponent(itemId)}`, json('PUT', input)),
  remove: (tripId: string, dayId: string, itemId: string) => api<void>(`${itineraryPath(tripId, dayId)}/${encodeURIComponent(itemId)}`, { method: 'DELETE' }),
  reorder: (tripId: string, dayId: string, itineraryIds: string[]) => api<Itinerary[]>(`${itineraryPath(tripId, dayId)}/order`, json('PUT', { itineraryIds })),
};

const diaryPath = (tripId: string, dayId: string) => `/trips/${encodeURIComponent(tripId)}/days/${encodeURIComponent(dayId)}/entries`;
export const diaryApi = {
  list: (tripId: string, dayId: string) => api<DiaryEntry[]>(diaryPath(tripId, dayId)),
  create: (tripId: string, dayId: string, input: DiaryEntryInput) => api<DiaryEntry>(diaryPath(tripId, dayId), json('POST', input)),
  update: (tripId: string, dayId: string, entryId: string, input: DiaryEntryInput) => api<DiaryEntry>(`${diaryPath(tripId, dayId)}/${encodeURIComponent(entryId)}`, json('PUT', input)),
  remove: (tripId: string, dayId: string, entryId: string) => api<void>(`${diaryPath(tripId, dayId)}/${encodeURIComponent(entryId)}`, { method: 'DELETE' }),
};
