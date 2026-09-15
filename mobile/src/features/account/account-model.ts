export const deletionWarning = '회원 정보, 여행, 일정, 기록과 생성한 다이어리가 삭제되며 복구할 수 없습니다. 사진도 삭제 처리됩니다. 저장소 오류가 발생한 사진은 후속 정리될 수 있습니다.';

export function accountMenu(privacyUrl?: string, termsUrl?: string) {
  const url = (value?: string) => {
    try { const parsed = new URL(value?.trim() ?? ''); return parsed.protocol === 'https:' && !parsed.username && !parsed.password ? parsed.toString() : undefined; }
    catch { return undefined; }
  };
  return [
    { id: 'privacy', label: '개인정보처리방침', url: url(privacyUrl) },
    { id: 'terms', label: '이용약관', url: url(termsUrl) },
    { id: 'delete', label: '회원 탈퇴', url: undefined },
  ];
}

export async function openPolicy(url: string | undefined, open: (url: string) => Promise<unknown>) {
  if (!url) return '아직 준비 중입니다.';
  try { await open(url); return undefined; }
  catch { return '링크를 열지 못했습니다. 잠시 후 다시 시도해 주세요.'; }
}

export async function deleteAccountAndClear(remove: () => Promise<void>, clear: () => Promise<void>) {
  await remove();
  await clear();
}

export function createAccountDeletionStore(remove: () => Promise<void>) {
  let state: { confirming: boolean; busy: boolean; deleted: boolean; error?: string } = { confirming: false, busy: false, deleted: false };
  const listeners = new Set<() => void>();
  const update = (patch: Partial<typeof state>) => { state = { ...state, ...patch }; listeners.forEach(listener => listener()); };
  return {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    request() { if (!state.busy && !state.deleted) update({ confirming: true, error: undefined }); },
    cancel() { if (!state.busy) update({ confirming: false, error: undefined }); },
    async confirm() {
      if (!state.confirming || state.busy || state.deleted) return;
      update({ busy: true, error: undefined });
      try { await remove(); update({ busy: false, confirming: false, deleted: true }); }
      catch (error) { update({ busy: false, error: error instanceof Error ? error.message : '회원 탈퇴를 처리하지 못했습니다. 다시 시도해 주세요.' }); }
    },
  };
}
