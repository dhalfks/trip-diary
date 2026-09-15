import type { ImageFile, UploadCheckpoint, UploadCompletion, UploadPhase, UploadTicket } from './types';

type Dependencies = {
  initiate(file: ImageFile): Promise<UploadTicket>;
  complete(imageId: string): Promise<UploadCompletion>;
  put(ticket: UploadTicket, file: ImageFile, progress: (percent: number) => void, signal: AbortSignal): Promise<void>;
};
type Update = (phase: UploadPhase, progress?: number) => void;
const code = (error: unknown) => (error as { code?: string } | null)?.code;
const status = (error: unknown) => (error as { status?: number } | null)?.status;
export const isRateLimited = (error: unknown) => status(error) === 429 || code(error) === 'RATE_LIMIT_EXCEEDED';

/** Mutable checkpoint belongs to one selected file and survives a user-triggered retry. */
export async function uploadImage(file: ImageFile, checkpoint: UploadCheckpoint, dependencies: Dependencies,
                                  update: Update, signal: AbortSignal, now = () => Date.now()): Promise<void> {
  const active = () => { if (signal.aborted) throw new Error('사진 업로드를 중단했어요.'); };
  const complete = async () => {
    active();
    update('completing', 100);
    const result = await dependencies.complete(checkpoint.ticket!.imageId);
    active();
    if (result.status !== 'COMPLETED' || result.imageId !== checkpoint.ticket!.imageId) throw new Error('업로드 완료를 확인하지 못했어요. 다시 시도해 주세요.');
    update('success', 100);
  };
  active();
  if (checkpoint.putSucceeded) {
    try { await complete(); return; }
    catch (error) {
      if (code(error) === 'IMAGE_UPLOAD_MISMATCH') {
        checkpoint.ticket = undefined; checkpoint.putSucceeded = false; checkpoint.putAttempted = false;
      }
      throw error;
    }
  }
  const expired = checkpoint.ticket && Date.parse(checkpoint.ticket.expiresAt) <= now() + 5000;
  if (checkpoint.ticket && (expired || checkpoint.renewTicket)) {
    if (checkpoint.putAttempted) {
      // A lost PUT response may still mean success. Reconcile before allocating a new image/key.
      try { await complete(); return; }
      catch (error) {
        if (!['IMAGE_UPLOAD_NOT_READY', 'IMAGE_UPLOAD_MISMATCH'].includes(code(error) ?? '')) throw error;
      }
    }
    checkpoint.ticket = undefined; checkpoint.putAttempted = false; checkpoint.renewTicket = false;
  }
  if (!checkpoint.ticket) {
    active(); update('signing', 0);
    checkpoint.ticket = await dependencies.initiate(file);
  }
  active(); update('uploading', 0);
  checkpoint.putAttempted = true;
  try {
    await dependencies.put(checkpoint.ticket, file, percent => { if (!signal.aborted) update('uploading', percent); }, signal);
    checkpoint.putSucceeded = true;
  } catch (error) {
    if (status(error) === 403) checkpoint.renewTicket = true;
    // Conditional PUT returns 412 if a previous attempt already stored this key.
    if (status(error) !== 412) throw error;
  }
  try { await complete(); }
  catch (error) {
    if (code(error) === 'IMAGE_UPLOAD_MISMATCH') {
      checkpoint.ticket = undefined; checkpoint.putSucceeded = false; checkpoint.putAttempted = false;
    }
    throw error;
  }
}
