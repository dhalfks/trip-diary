import assert from 'node:assert/strict';
import { test } from 'node:test';
import { photoMetadata, MAX_IMAGE_BYTES } from '../src/features/images/photo-metadata.ts';
import { isRateLimited, uploadImage } from '../src/features/images/upload-flow.ts';
import { putImage, S3UploadError } from '../src/features/images/s3-upload.ts';

const file = { uri: 'file:///photo.jpg', originalFileName: 'photo.jpg', contentType: 'image/jpeg', fileSize: 1234 };
const ticket = () => ({ imageId: 'image-1', status: 'PENDING', method: 'PUT', uploadUrl: 'https://private.example.test/images/key',
  headers: { 'content-type': ['image/jpeg'], 'content-length': ['1234'], 'if-none-match': ['*'] }, expiresAt: new Date(Date.now() + 300000).toISOString() });
const missing = () => Object.assign(new Error('missing'), { code: 'IMAGE_UPLOAD_NOT_READY' });
function fixture(overrides = {}) {
  const calls = [];
  return { calls, dependencies: {
    initiate: async value => { calls.push(['sign', value]); return ticket(); },
    put: async (_ticket, _file, progress) => { calls.push(['put']); progress(50); },
    complete: async imageId => { calls.push(['complete', imageId]); return { imageId, status: 'COMPLETED' }; },
    ...overrides,
  } };
}
const signal = () => new AbortController().signal;

test('upload-url rate limit is distinct from S3 failure and sends no binary', async () => {
  const limited = Object.assign(new Error('잠시 후 다시 시도해 주세요.'), { status: 429, code: 'RATE_LIMIT_EXCEEDED' });
  const { calls, dependencies } = fixture({ initiate: async () => { throw limited; } });
  const checkpoint = {};
  await assert.rejects(uploadImage(file, checkpoint, dependencies, () => {}, signal()), error => isRateLimited(error));
  assert.deepEqual(calls, []); assert.equal(checkpoint.ticket, undefined);
  assert.equal(isRateLimited(new Error('network error')), false);
});

test('complete rate limit preserves the successful PUT and retries only completion', async () => {
  let limited = true;
  const { calls, dependencies } = fixture({ complete: async imageId => {
    calls.push(['complete', imageId]);
    if (limited) throw Object.assign(new Error('잠시 후 다시 시도해 주세요.'), { status: 429 });
    return { imageId, status: 'COMPLETED' };
  } });
  const checkpoint = {};
  await assert.rejects(uploadImage(file, checkpoint, dependencies, () => {}, signal()), error => isRateLimited(error));
  assert.equal(checkpoint.putSucceeded, true); assert.equal(checkpoint.ticket.imageId, 'image-1');
  limited = false; await uploadImage(file, checkpoint, dependencies, () => {}, signal());
  assert.deepEqual(calls.map(call => call[0]), ['sign', 'put', 'complete', 'complete']);
});

test('selection metadata uses actual file size and normalizes converted HEIC names', () => {
  const result = photoMetadata({ uri: 'file:///converted.jpg', fileName: 'IMG_001.HEIC', mimeType: 'image/jpeg', fileSize: 400 });
  assert.equal(result.originalFileName, 'IMG_001.jpg');
  assert.equal(result.contentType, 'image/jpeg');
  assert.equal(result.fileSize, 400);
});
test('missing names and MIME types can use the local file extension', () => {
  const result = photoMetadata({ uri: 'file:///image.png', fileSize: 1 });
  assert.equal(result.originalFileName, 'image.png');
  assert.equal(result.contentType, 'image/png');
});
test('unsupported formats, empty files and oversized files are rejected', () => {
  for (const size of [0, -1, NaN, MAX_IMAGE_BYTES + 1]) {
    assert.throws(() => photoMetadata({ uri: file.uri, fileSize: size }));
  }
  assert.throws(() => photoMetadata({ uri: 'file:///image.svg', mimeType: 'image/svg+xml', fileSize: 1 }));
  assert.equal(photoMetadata({ uri: file.uri, fileSize: MAX_IMAGE_BYTES }).fileSize, MAX_IMAGE_BYTES);
});
test('filename is safe and within backend limits', () => {
  const result = photoMetadata({ uri: file.uri, fileName: '../a\\b\n' + 'a'.repeat(300) + '.jpg', fileSize: 1 });
  assert.ok(result.originalFileName.length <= 255);
  assert.doesNotMatch(result.originalFileName, /[/\\\n]/);
});
test('upload signs metadata, PUTs the same file and completes only after PUT', async () => {
  const { calls, dependencies } = fixture();
  const updates = [];
  await uploadImage(file, {}, dependencies, (...args) => updates.push(args), signal());
  assert.deepEqual(calls.map(call => call[0]), ['sign', 'put', 'complete']);
  assert.equal(calls[0][1], file);
  assert.deepEqual(updates, [['signing', 0], ['uploading', 0], ['uploading', 50], ['completing', 100], ['success', 100]]);
});
test('PUT failure preserves ticket and same selected file for retry', async () => {
  const state = {};
  const { calls, dependencies } = fixture();
  let attempts = 0;
  dependencies.put = async (_ticket, selected) => { assert.equal(selected, file); if (++attempts === 1) throw new Error('offline'); };
  await assert.rejects(uploadImage(file, state, dependencies, () => {}, signal()));
  assert.equal(calls.filter(call => call[0] === 'complete').length, 0);
  await uploadImage(file, state, dependencies, () => {}, signal());
  assert.equal(calls.filter(call => call[0] === 'sign').length, 1);
  assert.equal(attempts, 2);
});
test('completion failure retries completion without uploading again', async () => {
  const { calls, dependencies } = fixture();
  const complete = dependencies.complete;
  let attempts = 0;
  dependencies.complete = async id => { if (++attempts === 1) throw new Error('timeout'); return complete(id); };
  const state = {};
  await assert.rejects(uploadImage(file, state, dependencies, () => {}, signal()));
  assert.equal(state.putSucceeded, true);
  await uploadImage(file, state, dependencies, () => {}, signal());
  assert.equal(calls.filter(call => call[0] === 'put').length, 1);
  assert.equal(calls.filter(call => call[0] === 'sign').length, 1);
});
test('expired unattempted URL is replaced before PUT', async () => {
  const { calls, dependencies } = fixture();
  await uploadImage(file, { ticket: { ...ticket(), expiresAt: '2000-01-01T00:00:00Z' } }, dependencies, () => {}, signal());
  assert.deepEqual(calls.map(call => call[0]), ['sign', 'put', 'complete']);
});
test('expired attempted URL first reconciles a potentially lost successful PUT', async () => {
  const { calls, dependencies } = fixture();
  await uploadImage(file, { ticket: { ...ticket(), expiresAt: '2000-01-01T00:00:00Z' }, putAttempted: true }, dependencies, () => {}, signal());
  assert.deepEqual(calls.map(call => call[0]), ['complete']);
});
test('expired attempted upload receives a new ticket only after confirmed missing', async () => {
  const { calls, dependencies } = fixture();
  const complete = dependencies.complete;
  let attempts = 0;
  dependencies.complete = async id => { if (++attempts === 1) throw missing(); return complete(id); };
  await uploadImage(file, { ticket: { ...ticket(), expiresAt: '2000-01-01T00:00:00Z' }, putAttempted: true }, dependencies, () => {}, signal());
  assert.deepEqual(calls.map(call => call[0]), ['sign', 'put', 'complete']);
  assert.equal(attempts, 2);
});
test('ambiguous HEAD errors preserve expired ticket instead of creating duplicate images', async () => {
  const { calls, dependencies } = fixture({ complete: async () => { throw Object.assign(new Error('unavailable'), { code: 'IMAGE_STORAGE_UNAVAILABLE' }); } });
  const state = { ticket: { ...ticket(), expiresAt: '2000-01-01T00:00:00Z' }, putAttempted: true };
  await assert.rejects(uploadImage(file, state, dependencies, () => {}, signal()));
  assert.equal(calls.length, 0);
  assert.ok(state.ticket);
});
test('conditional PUT 412 completes an already existing upload', async () => {
  const { calls, dependencies } = fixture({ put: async () => { throw new S3UploadError(412, 'exists'); } });
  await uploadImage(file, { ticket: ticket() }, dependencies, () => {}, signal());
  assert.deepEqual(calls.map(call => call[0]), ['complete']);
});
test('PUT 403 asks for renewal on the next retry', async () => {
  const { dependencies } = fixture({ put: async () => { throw new S3UploadError(403, 'expired'); } });
  const state = { ticket: ticket() };
  await assert.rejects(uploadImage(file, state, dependencies, () => {}, signal()));
  assert.equal(state.renewTicket, true);
});
test('metadata mismatch clears unusable upload so selected file can be retried', async () => {
  const { dependencies } = fixture({ complete: async () => { throw Object.assign(new Error('mismatch'), { code: 'IMAGE_UPLOAD_MISMATCH' }); } });
  const state = {};
  await assert.rejects(uploadImage(file, state, dependencies, () => {}, signal()));
  assert.equal(state.ticket, undefined);
  assert.equal(state.putSucceeded, false);
});
test('abort during signing prevents PUT and completion', async () => {
  const controller = new AbortController();
  const { calls, dependencies } = fixture({ initiate: async () => { controller.abort(); return ticket(); } });
  await assert.rejects(uploadImage(file, {}, dependencies, () => {}, controller.signal));
  assert.equal(calls.length, 0);
});
test('incorrect completion response cannot report success', async () => {
  const { dependencies } = fixture({ complete: async () => ({ imageId: 'wrong-id', status: 'COMPLETED' }) });
  const phases = [];
  await assert.rejects(uploadImage(file, {}, dependencies, phase => phases.push(phase), signal()));
  assert.ok(!phases.includes('success'));
});

class FakeXhr {
  upload = {};
  headers = {};
  status = 200;
  open(method, url) { this.method = method; this.url = url; }
  setRequestHeader(name, value) { this.headers[name] = value; }
  send(body) { this.body = body; }
  abort() { this.onabort?.(); }
}
test('S3 transport sends raw native URI, signed headers and real progress without JWT', async () => {
  const xhr = new FakeXhr();
  const progress = [];
  const promise = putImage(ticket(), file, value => progress.push(value), signal(), () => xhr);
  assert.equal(xhr.method, 'PUT');
  assert.equal(xhr.withCredentials, false);
  assert.deepEqual(xhr.body, { uri: file.uri });
  assert.deepEqual(xhr.headers, { 'content-type': 'image/jpeg', 'if-none-match': '*' });
  xhr.upload.onprogress({ lengthComputable: true, loaded: 617, total: 1234 });
  assert.deepEqual(progress, [50]);
  xhr.onload();
  await promise;
});
test('web transport sends the original File as its raw body', async () => {
  const xhr = new FakeXhr();
  const webFile = new File(['photo'], 'photo.jpg', { type: 'image/jpeg' });
  const promise = putImage(ticket(), { ...file, webFile }, () => {}, signal(), () => xhr);
  assert.equal(xhr.body, webFile);
  xhr.onload();
  await promise;
});
test('HTTP failure, network failure, timeout and cancellation reject transport', async () => {
  for (const event of ['onload', 'onerror', 'ontimeout', 'onabort']) {
    const xhr = new FakeXhr();
    xhr.status = 403;
    const promise = putImage(ticket(), file, () => {}, signal(), () => xhr);
    xhr[event]();
    await assert.rejects(promise);
  }
});
test('unsafe upload URL and auth headers never send a file', async () => {
  for (const invalid of [{ ...ticket(), uploadUrl: 'http://example.test' }, { ...ticket(), headers: { Authorization: ['Bearer forbidden'] } }]) {
    const xhr = new FakeXhr();
    await assert.rejects(putImage(invalid, file, () => {}, signal(), () => xhr));
    assert.equal(xhr.body, undefined);
  }
});
