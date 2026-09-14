import test from 'node:test';
import assert from 'node:assert/strict';
import { createBookStore, createPreviewStore } from '../src/features/books/book-store.ts';
import { previewPage, pageModel, collectCoverImages } from '../src/features/books/book-model.ts';

const book = id => ({ id, title: id, templateType: 'CLASSIC', pageCount: 1 });
const page = (order, content = {}) => ({ id: `page-${order}`, pageOrder: order, layoutType: 'PHOTO_TEXT', content });
const detail = (pages = [page(1)], images = []) => ({ diary: book('diary'), pages, images });
const api = (overrides = {}) => ({ list: async () => [], create: async () => book('new'), remove: async () => {}, ...overrides });
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

test('generation sends template and cover, retains the previous independent diary', async () => {
  let received;
  const store = createBookStore(api({ list: async () => [book('old')], create: async input => { received = input; return book('new'); } }));
  await store.load();
  const input = { title: 'Summer', templateType: 'PHOTO', coverImageId: 'image' };
  assert.equal((await store.create(input)).id, 'new');
  assert.deepEqual(received, input);
  assert.deepEqual(store.getSnapshot().diaries.map(item => item.id), ['new', 'old']);
  assert.equal(store.getSnapshot().busy, false);
  assert.ok(store.getSnapshot().notice);
});

test('duplicate generation taps are ignored while generation is pending', async () => {
  const pending = deferred(); let calls = 0;
  const store = createBookStore(api({ create: () => { calls++; return pending.promise; } }));
  const first = store.create({ templateType: 'CLASSIC' });
  assert.equal(store.getSnapshot().busy, true);
  assert.equal(await store.create({ templateType: 'PHOTO' }), undefined);
  pending.resolve(book('new')); await first;
  assert.equal(calls, 1);
});

test('generation failure is visible and can be retried', async () => {
  let fail = true;
  const store = createBookStore(api({ create: async () => { if (fail) throw new Error('limit reached'); return book('retry'); } }));
  await store.create({ templateType: 'CLASSIC' });
  assert.equal(store.getSnapshot().error, 'limit reached');
  assert.equal(store.getSnapshot().busy, false);
  fail = false; await store.create({ templateType: 'CLASSIC' });
  assert.equal(store.getSnapshot().error, undefined);
  assert.equal(store.getSnapshot().diaries[0].id, 'retry');
});

test('empty diary list is a successful state', async () => {
  const store = createBookStore(api()); await store.load();
  assert.deepEqual(store.getSnapshot().diaries, []);
  assert.equal(store.getSnapshot().loading, false);
  assert.equal(store.getSnapshot().error, undefined);
});

test('lost trip access clears the previously loaded diary list', async () => {
  let denied = false;
  const store = createBookStore(api({ list: async () => {
    if (denied) throw Object.assign(new Error('not found'), { status: 404 });
    return [book('private')];
  } }));
  await store.load(); denied = true; await store.load();
  assert.deepEqual(store.getSnapshot().diaries, []);
  assert.equal(store.getSnapshot().error, 'not found');
});

test('delete shows pending state and removes only the selected result', async () => {
  const pending = deferred(); let removed;
  const store = createBookStore(api({ list: async () => [book('a'), book('b')], remove: id => { removed = id; return pending.promise; } }));
  await store.load(); const deletion = store.remove('a');
  assert.equal(store.getSnapshot().deletingId, 'a');
  pending.resolve(); assert.equal(await deletion, true);
  assert.equal(removed, 'a');
  assert.deepEqual(store.getSnapshot().diaries.map(item => item.id), ['b']);
  assert.equal(store.getSnapshot().deletingId, undefined);
});

test('failed deletion preserves the diary; already deleted response cleans up the list', async () => {
  let code = 'STORAGE_ERROR';
  const store = createBookStore(api({ list: async () => [book('a')], remove: async () => { throw Object.assign(new Error('failed'), { code }); } }));
  await store.load(); assert.equal(await store.remove('a'), false);
  assert.equal(store.getSnapshot().diaries.length, 1);
  assert.equal(store.getSnapshot().error, 'failed');
  code = 'DIARY_NOT_FOUND'; assert.equal(await store.remove('a'), true);
  assert.deepEqual(store.getSnapshot().diaries, []);
});

test('a stale list response cannot overwrite a completed generation', async () => {
  const pending = deferred();
  const store = createBookStore(api({ list: () => pending.promise }));
  const loading = store.load(); await store.create({ templateType: 'CLASSIC' });
  pending.resolve([]); await loading;
  assert.equal(store.getSnapshot().diaries[0].id, 'new');
});

test('preview sorts pages and clamps previous/next navigation', async () => {
  const store = createPreviewStore(async () => detail([page(3), page(1), page(2)]));
  await store.load();
  assert.deepEqual(store.getSnapshot().detail.pages.map(item => item.pageOrder), [1, 2, 3]);
  store.move(-1); assert.equal(store.getSnapshot().index, 0);
  store.move(1); assert.equal(previewPage(store.getSnapshot().detail, store.getSnapshot().index).id, 'page-2');
  store.move(20); assert.equal(store.getSnapshot().index, 2);
});

test('page models resolve private signed URLs while preserving missing photo placeholders', () => {
  const signed = { id: 'photo', downloadUrl: 'https://private.example/key?signature=temporary' };
  const model = pageModel(page(1, { title: 'Day 1', text: 'Diary text', imageIds: ['missing', 'photo'] }), [signed]);
  assert.equal(model.text, 'Diary text');
  assert.equal(model.photos[0].image, undefined);
  assert.equal(model.photos[1].image.downloadUrl, signed.downloadUrl);
});

test('blank or malformed page content and empty previews are safe', async () => {
  assert.equal(previewPage(undefined, 0), undefined);
  const store = createPreviewStore(async () => detail([])); await store.load(); store.move(1);
  assert.equal(store.getSnapshot().index, 0);
  assert.equal(previewPage(store.getSnapshot().detail, 0), undefined);
  const model = pageModel(page(1, { title: null, text: 3, imageIds: [1, null] }), []);
  assert.equal(model.title, ''); assert.equal(model.text, ''); assert.deepEqual(model.photos, []);
});

test('preview refresh replaces signed URLs without resetting the current page', async () => {
  let url = 'old';
  const store = createPreviewStore(async () => detail([page(1), page(2, { imageIds: ['p'] })], [{ id: 'p', downloadUrl: url }]));
  await store.load(); store.move(1); url = 'new'; await store.load();
  assert.equal(store.getSnapshot().index, 1);
  assert.equal(previewPage(store.getSnapshot().detail, 1).photos[0].image.downloadUrl, 'new');
  assert.equal(store.getSnapshot().revision, 2);
});

test('lost access clears private preview data', async () => {
  let status;
  const store = createPreviewStore(async () => { if (status) throw Object.assign(new Error('forbidden'), { status }); return detail(); });
  await store.load(); status = 403; await store.load();
  assert.equal(store.getSnapshot().detail, undefined);
  assert.equal(store.getSnapshot().error, 'forbidden');
});

test('latest preview response wins and unmount ignores pending work', async () => {
  const first = deferred(); let calls = 0;
  const store = createPreviewStore(() => ++calls === 1 ? first.promise : Promise.resolve(detail([page(2)])));
  const loading = store.load(); await store.load(); first.resolve(detail([page(1)])); await loading;
  assert.equal(store.getSnapshot().detail.pages[0].id, 'page-2');
  const pending = deferred(); const cancelled = createPreviewStore(() => pending.promise);
  const request = cancelled.load(); cancelled.cancel(); pending.resolve(detail()); await request;
  assert.equal(cancelled.getSnapshot().detail, undefined);
});

test('cover choices reuse entry/image APIs in chronological deterministic order', async () => {
  let active = 0, maximum = 0;
  const photos = await collectCoverImages([{ id: 'late', date: '2026-09-02' }, { id: 'early', date: '2026-09-01' }], {
    entries: async day => [{ id: `${day}-b`, createdAt: 'same' }, { id: `${day}-a`, createdAt: 'same' }],
    images: async (_day, entry) => {
      maximum = Math.max(maximum, ++active); await Promise.resolve(); active--;
      return [{ id: `${entry}-2`, createdAt: 'same' }, { id: `${entry}-1`, createdAt: 'same' }];
    },
  });
  assert.deepEqual(photos.map(photo => photo.id), ['early-a-1', 'early-a-2', 'early-b-1', 'early-b-2', 'late-a-1', 'late-a-2', 'late-b-1', 'late-b-2']);
  assert.ok(maximum <= 4);
  assert.deepEqual(await collectCoverImages([], { entries: async () => [], images: async () => [] }), []);
});
