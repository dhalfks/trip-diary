import { api, jsonBody } from '@/lib/api';
import type { DiaryDetail, GenerateDiary, TravelDiary } from './types';

const path = (tripId: string) => `/trips/${encodeURIComponent(tripId)}/diaries`;
export const bookApi = {
  list: (tripId: string) => api<TravelDiary[]>(path(tripId)),
  create: (tripId: string, input: GenerateDiary) => api<TravelDiary>(path(tripId), jsonBody(input)),
  get: (tripId: string, diaryId: string) => api<DiaryDetail>(`${path(tripId)}/${encodeURIComponent(diaryId)}`),
  remove: (tripId: string, diaryId: string) => api<void>(`${path(tripId)}/${encodeURIComponent(diaryId)}`, { method: 'DELETE' }),
};
