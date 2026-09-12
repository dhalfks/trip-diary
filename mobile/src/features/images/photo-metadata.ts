import type { ImageFile } from './types';

const extensions: Record<string, string[]> = {
  'image/jpeg': ['jpg', 'jpeg'], 'image/png': ['png'], 'image/webp': ['webp'],
  'image/heic': ['heic'], 'image/heif': ['heif'],
};
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

export function photoMetadata(input: { uri: string; fileName?: string | null; mimeType?: string | null; fileSize: number }): ImageFile {
  if (!Number.isSafeInteger(input.fileSize) || input.fileSize < 1) throw new Error('사진 파일을 읽을 수 없어요. 다시 선택해 주세요.');
  if (input.fileSize > MAX_IMAGE_BYTES) throw new Error('사진은 한 장당 10MB 이하로 선택해 주세요.');
  const uriName = decodeURIComponent(input.uri.split('?')[0].split('/').pop() ?? '');
  const rawName = input.fileName || uriName;
  const uriExtension = uriName.split('.').pop()?.toLowerCase();
  const nameExtension = rawName.split('.').pop()?.toLowerCase();
  const inferredType = Object.keys(extensions).find(type => extensions[type].includes(uriExtension ?? ''))
    ?? Object.keys(extensions).find(type => extensions[type].includes(nameExtension ?? ''));
  const reportedType = input.mimeType?.toLowerCase();
  const contentType = reportedType && reportedType !== 'application/octet-stream' ? reportedType : inferredType;
  if (!contentType || !extensions[contentType]) throw new Error('JPEG, PNG, WebP, HEIC, HEIF 사진을 선택해 주세요.');
  // Pickers may return a converted file while retaining the original asset's filename.
  const extension = extensions[contentType].includes(nameExtension ?? '') ? nameExtension! : extensions[contentType][0];
  const stem = rawName.replace(/\.[^.]*$/, '').replace(/[\x00-\x1f\x7f-\x9f/\\]/g, '_').trim().slice(0, 240) || 'photo';
  return { uri: input.uri, originalFileName: `${stem}.${extension}`, contentType, fileSize: input.fileSize };
}
