import type { DiaryImage } from './types';

type GalleryState = {
  images: DiaryImage[]; loading: boolean; revision: number; deletingId?: string; error?: string; notice?: string;
};
type GalleryApi = { list(): Promise<DiaryImage[]>; remove(imageId: string): Promise<void> };
const message = (error: unknown) => error instanceof Error ? error.message : '사진 요청을 처리하지 못했어요.';

/** Per-entry, in-memory state. Request generations prevent old GETs from undoing a deletion. */
export function createImageGallery(api: GalleryApi) {
  let state: GalleryState = { images: [], loading: true, revision: 0 };
  const listeners = new Set<() => void>();
  const deleted = new Set<string>();
  let request = 0;
  let lifetime = 0;
  const update = (patch: Partial<GalleryState>) => {
    state = { ...state, ...patch };
    listeners.forEach(listener => listener());
  };
  return {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    cancel: () => { request++; lifetime++; },
    async load() {
      const current = ++request;
      update({ loading: true, error: undefined });
      try {
        const images = await api.list();
        if (current === request) update({ images: images.filter(image => !deleted.has(image.id)), loading: false, revision: state.revision + 1 });
      } catch (error) {
        if (current !== request) return;
        const status = (error as { status?: number } | null)?.status;
        update({ loading: false, error: message(error), ...([401, 403, 404].includes(status ?? 0) ? { images: [] } : {}) });
      }
    },
    async remove(imageId: string) {
      if (state.deletingId) return false;
      const currentLifetime = lifetime;
      ++request;
      update({ loading: false, deletingId: imageId, error: undefined, notice: undefined });
      try {
        try { await api.remove(imageId); }
        catch (error) {
          // A previous successful DELETE may have lost its response. Only image-specific 404 is success.
          if ((error as { code?: string } | null)?.code !== 'IMAGE_NOT_FOUND') throw error;
        }
        if (currentLifetime !== lifetime) return false;
        deleted.add(imageId);
        ++request;
        update({ images: state.images.filter(image => image.id !== imageId), loading: false,
          deletingId: undefined, notice: '사진을 삭제했어요.' });
        return true;
      } catch (error) {
        if (currentLifetime === lifetime) update({ deletingId: undefined, error: message(error) });
        return false;
      }
    },
  };
}

export function imageRefreshDelay(images: DiaryImage[], now = Date.now()): number | undefined {
  if (!images.length) return undefined;
  const expires = Math.min(...images.map(image => Date.parse(image.expiresAt)));
  // Refresh shortly before expiry; never spin immediately on malformed/expired responses.
  return Number.isFinite(expires) ? Math.max(5000, expires - now - 10000) : undefined;
}
