import type { BookApi, DiaryDetail, GenerateDiary, TravelDiary } from './types';

const message = (error: unknown) => error instanceof Error ? error.message : '다이어리 요청을 처리하지 못했어요.';

export function createBookStore(api: BookApi) {
  let state: { diaries: TravelDiary[]; loading: boolean; busy: boolean; deletingId?: string; error?: string; notice?: string } = {
    diaries: [], loading: true, busy: false,
  };
  const listeners = new Set<() => void>();
  let request = 0, lifetime = 0;
  const update = (patch: Partial<typeof state>) => { state = { ...state, ...patch }; listeners.forEach(listener => listener()); };
  return {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    cancel: () => { request++; lifetime++; },
    async load() {
      const current = ++request;
      update({ loading: true, error: undefined });
      try { const diaries = await api.list(); if (current === request) update({ diaries, loading: false }); }
      catch (error) {
        if (current !== request) return;
        const status = (error as { status?: number } | null)?.status;
        update({ loading: false, error: message(error), ...([401, 403, 404].includes(status ?? 0) ? { diaries: [] } : {}) });
      }
    },
    async create(input: GenerateDiary) {
      if (state.busy) return;
      const current = lifetime;
      ++request; update({ busy: true, loading: false, error: undefined, notice: undefined });
      try {
        const diary = await api.create(input);
        if (current !== lifetime) return;
        ++request;
        update({ diaries: [diary, ...state.diaries.filter(item => item.id !== diary.id)], busy: false, notice: '여행 다이어리가 완성됐어요.' });
        return diary;
      } catch (error) { if (current === lifetime) update({ busy: false, error: message(error) }); }
    },
    async remove(id: string) {
      if (state.busy) return false;
      const current = lifetime;
      ++request; update({ busy: true, loading: false, deletingId: id, error: undefined, notice: undefined });
      try {
        try { await api.remove(id); }
        catch (error) { if ((error as { code?: string } | null)?.code !== 'DIARY_NOT_FOUND') throw error; }
        if (current !== lifetime) return false;
        ++request;
        update({ diaries: state.diaries.filter(diary => diary.id !== id), busy: false, deletingId: undefined, notice: '생성된 다이어리를 삭제했어요.' });
        return true;
      } catch (error) { if (current === lifetime) update({ busy: false, deletingId: undefined, error: message(error) }); return false; }
    },
  };
}

export function createPreviewStore(get: () => Promise<DiaryDetail>) {
  let state: { detail?: DiaryDetail; index: number; loading: boolean; revision: number; error?: string } = {
    index: 0, loading: true, revision: 0,
  };
  let request = 0;
  const listeners = new Set<() => void>();
  const update = (patch: Partial<typeof state>) => { state = { ...state, ...patch }; listeners.forEach(listener => listener()); };
  return {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    cancel: () => { request++; },
    async load() {
      const current = ++request;
      update({ loading: true, error: undefined });
      try {
        const response = await get();
        if (current !== request) return;
        const detail = { ...response, pages: [...response.pages].sort((a, b) => a.pageOrder - b.pageOrder) };
        update({ detail, index: Math.min(state.index, Math.max(0, detail.pages.length - 1)), loading: false, revision: state.revision + 1 });
      } catch (error) {
        if (current !== request) return;
        const status = (error as { status?: number } | null)?.status;
        update({ loading: false, error: message(error), ...([401, 403, 404].includes(status ?? 0) ? { detail: undefined } : {}) });
      }
    },
    move(offset: number) { update({ index: Math.max(0, Math.min(state.index + offset, (state.detail?.pages.length ?? 1) - 1)) }); },
  };
}
