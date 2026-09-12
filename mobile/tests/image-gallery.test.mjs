import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createImageGallery, imageRefreshDelay } from '../src/features/images/image-gallery-store.ts';

const photo = id => ({ id, diaryEntryId: 'entry', originalFileName: `${id}.jpg`, contentType: 'image/jpeg',
  fileSize: 100, createdAt: '2026-09-11T00:00:00Z', downloadUrl: `https://private.example.test/${id}?signature=test`,
  expiresAt: '2026-09-11T00:05:00Z' });
function deferred() {
  let resolve, reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

test('loads private image responses and empty entries without special cases', async () => {
  let result = [photo('one')];
  const gallery = createImageGallery({ list: async () => result, remove: async () => {} });
  await gallery.load();
  assert.deepEqual(gallery.getSnapshot().images, result);
  assert.equal(gallery.getSnapshot().loading, false);
  result = [];
  await gallery.load();
  assert.deepEqual(gallery.getSnapshot().images, []);
});
test('list errors are visible and a retry recovers', async () => {
  let failed = true;
  const gallery = createImageGallery({ list: async () => { if (failed) throw new Error('offline'); return [photo('one')]; }, remove: async () => {} });
  await gallery.load();
  assert.equal(gallery.getSnapshot().error, 'offline');
  assert.equal(gallery.getSnapshot().loading, false);
  failed = false;
  await gallery.load();
  assert.equal(gallery.getSnapshot().error, undefined);
  assert.equal(gallery.getSnapshot().images.length, 1);
});
test('refresh after completion includes new images and resets thumbnail retry revision', async () => {
  const saved = [photo('one')];
  const gallery = createImageGallery({ list: async () => [...saved], remove: async () => {} });
  await gallery.load();
  const revision = gallery.getSnapshot().revision;
  saved.push(photo('uploaded'));
  await gallery.load();
  assert.equal(gallery.getSnapshot().images.length, 2);
  assert.ok(gallery.getSnapshot().revision > revision);
});
test('delete waits for success and reports deleting and success states', async () => {
  const request = deferred();
  const gallery = createImageGallery({ list: async () => [photo('one')], remove: id => { assert.equal(id, 'one'); return request.promise; } });
  await gallery.load();
  const deletion = gallery.remove('one');
  assert.equal(gallery.getSnapshot().deletingId, 'one');
  assert.equal(gallery.getSnapshot().images.length, 1);
  request.resolve();
  assert.equal(await deletion, true);
  assert.deepEqual(gallery.getSnapshot().images, []);
  assert.equal(gallery.getSnapshot().deletingId, undefined);
  assert.equal(gallery.getSnapshot().notice, '사진을 삭제했어요.');
});
test('delete failure preserves the image and allows retry', async () => {
  let failed = true;
  const gallery = createImageGallery({ list: async () => [photo('one')], remove: async () => { if (failed) throw new Error('storage unavailable'); } });
  await gallery.load();
  assert.equal(await gallery.remove('one'), false);
  assert.equal(gallery.getSnapshot().images.length, 1);
  assert.equal(gallery.getSnapshot().error, 'storage unavailable');
  assert.equal(gallery.getSnapshot().deletingId, undefined);
  failed = false;
  assert.equal(await gallery.remove('one'), true);
});
test('image-specific not found reconciles a lost DELETE response', async () => {
  const gallery = createImageGallery({ list: async () => [photo('one')], remove: async () => { throw Object.assign(new Error('gone'), { status: 404, code: 'IMAGE_NOT_FOUND' }); } });
  await gallery.load();
  assert.equal(await gallery.remove('one'), true);
  assert.equal(gallery.getSnapshot().images.length, 0);
});
test('wrong entry and unauthorized DELETE do not report deletion success', async () => {
  for (const code of ['DIARY_ENTRY_NOT_FOUND', 'TRIP_DAY_NOT_FOUND', 'UNAUTHORIZED']) {
    const gallery = createImageGallery({ list: async () => [photo('one')], remove: async () => { throw Object.assign(new Error(code), { code }); } });
    await gallery.load();
    assert.equal(await gallery.remove('one'), false);
    assert.equal(gallery.getSnapshot().images.length, 1);
    assert.equal(gallery.getSnapshot().notice, undefined);
  }
});
test('duplicate delete presses issue only one request', async () => {
  const request = deferred();
  let calls = 0;
  const gallery = createImageGallery({ list: async () => [photo('one')], remove: () => { calls++; return request.promise; } });
  await gallery.load();
  const deletion = gallery.remove('one');
  assert.equal(await gallery.remove('one'), false);
  assert.equal(calls, 1);
  request.resolve();
  await deletion;
});
test('a late GET cannot restore a deleted image', async () => {
  let result = Promise.resolve([photo('one')]);
  const gallery = createImageGallery({ list: () => result, remove: async () => {} });
  await gallery.load();
  const request = deferred(); result = request.promise;
  const loading = gallery.load();
  await gallery.remove('one');
  request.resolve([photo('one')]);
  await loading;
  assert.deepEqual(gallery.getSnapshot().images, []);
  // Even a later stale upstream response must not reintroduce a locally confirmed deletion.
  await gallery.load();
  assert.deepEqual(gallery.getSnapshot().images, []);
});
test('newer list response wins over an older request', async () => {
  const first = deferred(), second = deferred();
  let calls = 0;
  const gallery = createImageGallery({ list: () => ++calls === 1 ? first.promise : second.promise, remove: async () => {} });
  const old = gallery.load(), recent = gallery.load();
  second.resolve([photo('new')]); await recent;
  first.resolve([photo('old')]); await old;
  assert.equal(gallery.getSnapshot().images[0].id, 'new');
});
test('loss of access clears old private image URLs', async () => {
  let revoked = false;
  const gallery = createImageGallery({ list: async () => { if (revoked) throw Object.assign(new Error('forbidden'), { status: 403 }); return [photo('one')]; }, remove: async () => {} });
  await gallery.load(); revoked = true; await gallery.load();
  assert.deepEqual(gallery.getSnapshot().images, []);
});
test('unmount cancellation ignores pending responses', async () => {
  const request = deferred();
  const gallery = createImageGallery({ list: () => request.promise, remove: async () => {} });
  const loading = gallery.load();
  gallery.cancel();
  request.resolve([photo('one')]); await loading;
  assert.deepEqual(gallery.getSnapshot().images, []);
});
test('URL refresh uses earliest expiration with no immediate retry loop', () => {
  const now = Date.parse('2026-09-11T00:00:00Z');
  assert.equal(imageRefreshDelay([photo('one')], now), 290000);
  assert.equal(imageRefreshDelay([photo('one'), { ...photo('two'), expiresAt: '2026-09-11T00:01:00Z' }], now), 50000);
  assert.equal(imageRefreshDelay([photo('one')], now + 300000), 5000);
  assert.equal(imageRefreshDelay([], now), undefined);
  assert.equal(imageRefreshDelay([{ ...photo('one'), expiresAt: 'invalid' }], now), undefined);
});
