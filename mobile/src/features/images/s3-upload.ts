import type { ImageFile, UploadTicket } from './types';

export class S3UploadError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.name = 'S3UploadError'; this.status = status; }
}

/** Separate from the authenticated API client: no bearer token, cookies, or multipart body. */
export function putImage(ticket: UploadTicket, file: ImageFile, onProgress: (percent: number) => void,
                         signal: AbortSignal, createRequest = () => new XMLHttpRequest()): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = createRequest();
    let settled = false;
    const finish = (error?: Error) => {
      if (settled) return;
      settled = true;
      signal.removeEventListener('abort', abort);
      xhr.onload = xhr.onerror = xhr.ontimeout = xhr.onabort = null;
      xhr.upload.onprogress = null;
      if (error) reject(error); else resolve();
    };
    const abort = () => { xhr.abort(); finish(new Error('사진 업로드를 중단했어요.')); };
    if (signal.aborted) return finish(new Error('사진 업로드를 중단했어요.'));
    try {
      if (ticket.method !== 'PUT' || new URL(ticket.uploadUrl).protocol !== 'https:') throw new Error('업로드 주소가 올바르지 않아요.');
      xhr.open('PUT', ticket.uploadUrl);
      xhr.withCredentials = false;
      xhr.timeout = 120000;
      for (const [name, values] of Object.entries(ticket.headers)) {
        const lower = name.toLowerCase();
        // Host and Content-Length are set by the transport from the URL and the exact raw body.
        if (lower === 'host' || lower === 'content-length') continue;
        if (['authorization', 'cookie'].includes(lower)) throw new Error('업로드 헤더가 올바르지 않아요.');
        xhr.setRequestHeader(name, values.join(', '));
      }
      xhr.upload.onprogress = event => {
        const total = event.lengthComputable ? event.total : file.fileSize;
        if (total > 0) onProgress(Math.min(100, Math.max(0, Math.round(event.loaded / total * 100))));
      };
      xhr.onload = () => xhr.status >= 200 && xhr.status < 300 ? finish()
        : finish(new S3UploadError(xhr.status, xhr.status === 403 ? '업로드 주소가 만료되었거나 사용할 수 없어요. 다시 시도해 주세요.' : '사진을 전송하지 못했어요. 다시 시도해 주세요.'));
      xhr.onerror = () => finish(new S3UploadError(0, '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'));
      xhr.ontimeout = () => finish(new S3UploadError(0, '사진 전송 시간이 초과되었어요. 다시 시도해 주세요.'));
      xhr.onabort = () => finish(new Error('사진 업로드를 중단했어요.'));
      signal.addEventListener('abort', abort, { once: true });
      // React Native supports { uri } as a raw file body (convertRequestBody), avoiding JS/base64 copies.
      xhr.send(file.webFile ?? ({ uri: file.uri } as unknown as XMLHttpRequestBodyInit));
    } catch { finish(new Error('사진 업로드를 시작하지 못했어요. 다시 시도해 주세요.')); }
  });
}
