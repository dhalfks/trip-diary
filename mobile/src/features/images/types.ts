export type ImageTarget = { tripId: string; dayId: string; entryId: string };
export type DiaryImage = {
  id: string; diaryEntryId: string; originalFileName: string; contentType: string;
  fileSize: number; createdAt: string; downloadUrl: string; expiresAt: string;
};
export type ImageFile = {
  uri: string; originalFileName: string; contentType: string; fileSize: number;
  webFile?: File;
};
export type UploadTicket = {
  imageId: string; status: 'PENDING'; method: 'PUT'; uploadUrl: string;
  headers: Record<string, string[]>; expiresAt: string;
};
export type UploadCompletion = { imageId: string; status: 'COMPLETED' };
export type UploadPhase = 'queued' | 'signing' | 'uploading' | 'completing' | 'success' | 'error';
export type UploadCheckpoint = { ticket?: UploadTicket; putSucceeded?: boolean; putAttempted?: boolean; renewTicket?: boolean };
export type UploadItem = {
  id: string; file: ImageFile; phase: UploadPhase; progress: number; error?: string; rateLimited?: boolean;
  checkpoint: UploadCheckpoint;
};
