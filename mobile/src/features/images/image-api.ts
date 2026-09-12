import { api, jsonBody } from '@/lib/api';
import type { DiaryImage, ImageFile, ImageTarget, UploadCompletion, UploadTicket } from './types';

const path = ({ tripId, dayId, entryId }: ImageTarget) =>
  `/trips/${encodeURIComponent(tripId)}/days/${encodeURIComponent(dayId)}/entries/${encodeURIComponent(entryId)}/images`;

export const imageApi = {
  list: (target: ImageTarget) => api<DiaryImage[]>(path(target)),
  remove: (target: ImageTarget, imageId: string) =>
    api<void>(`${path(target)}/${encodeURIComponent(imageId)}`, { method: 'DELETE' }),
  initiate: (target: ImageTarget, file: ImageFile) => api<UploadTicket>(`${path(target)}/upload-url`, jsonBody({
    originalFileName: file.originalFileName, contentType: file.contentType, fileSize: file.fileSize,
  })),
  complete: (target: ImageTarget, imageId: string) =>
    api<UploadCompletion>(`${path(target)}/${encodeURIComponent(imageId)}/complete`, { method: 'POST' }),
};
