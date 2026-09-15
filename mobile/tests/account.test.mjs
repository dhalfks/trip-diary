import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';
import { accountMenu, openPolicy, createAccountDeletionStore, deleteAccountAndClear, deletionWarning } from '../src/features/account/account-model.ts';

const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; };

test('settings offers privacy, terms and account deletion with honest placeholders', () => {
  const menu = accountMenu();
  assert.deepEqual(menu.map(item => item.label), ['개인정보처리방침', '이용약관', '회원 탈퇴']);
  assert.equal(menu[0].url, undefined); assert.equal(menu[1].url, undefined);
  assert.ok(deletionWarning.includes('복구할 수 없습니다'));
});

test('published policy links require HTTPS and reject credentials or executable URLs', () => {
  const menu = accountMenu('https://example.test/privacy', 'https://example.test/terms');
  assert.equal(menu[0].url, 'https://example.test/privacy');
  assert.equal(menu[1].url, 'https://example.test/terms');
  for (const url of ['javascript:alert(1)', 'http://example.test', 'https://user:password@example.test', 'invalid']) assert.equal(accountMenu(url)[0].url, undefined);
});

test('policy link opens the configured page and handles placeholders and failures', async () => {
  const opened = [];
  assert.equal(await openPolicy('https://example.test/privacy', async url => opened.push(url)), undefined);
  assert.deepEqual(opened, ['https://example.test/privacy']);
  assert.ok(await openPolicy(undefined, async url => opened.push(url)));
  assert.equal(opened.length, 1);
  assert.ok(await openPolicy('https://example.test/terms', async () => { throw new Error('offline'); }));
});

test('deletion requires opening the confirmation and cancelling sends no request', async () => {
  let calls = 0; const store = createAccountDeletionStore(async () => { calls++; });
  await store.confirm(); assert.equal(calls, 0);
  store.request(); assert.equal(store.getSnapshot().confirming, true);
  store.cancel(); await store.confirm();
  assert.equal(store.getSnapshot().confirming, false); assert.equal(calls, 0);
});

test('pending deletion shows loading, disables cancel and ignores repeated confirmation', async () => {
  const pending = deferred(); let calls = 0;
  const store = createAccountDeletionStore(() => { calls++; return pending.promise; });
  store.request(); const result = store.confirm();
  assert.equal(store.getSnapshot().busy, true); store.cancel();
  assert.equal(store.getSnapshot().confirming, true); await store.confirm();
  pending.resolve(); await result;
  assert.equal(calls, 1); assert.equal(store.getSnapshot().deleted, true);
  assert.equal(store.getSnapshot().busy, false); assert.equal(store.getSnapshot().confirming, false);
});

test('successful DELETE clears stored authentication and enters the signed-out state', async () => {
  const events = []; let authenticated = true;
  await deleteAccountAndClear(async () => events.push('DELETE /users/me'), async () => { events.push('clear tokens'); authenticated = false; });
  assert.deepEqual(events, ['DELETE /users/me', 'clear tokens']);
  // The existing Stack.Protected guard uses this state to show the auth/login stack.
  assert.equal(authenticated, false);
});

test('failed DELETE preserves the session and supports retry', async () => {
  let fail = true, cleared = false;
  const store = createAccountDeletionStore(() => deleteAccountAndClear(async () => { if (fail) throw new Error('offline'); }, async () => { cleared = true; }));
  store.request(); await store.confirm();
  assert.equal(store.getSnapshot().error, 'offline'); assert.equal(store.getSnapshot().busy, false);
  assert.equal(store.getSnapshot().confirming, true); assert.equal(cleared, false);
  fail = false; await store.confirm();
  assert.equal(store.getSnapshot().deleted, true); assert.equal(store.getSnapshot().error, undefined); assert.equal(cleared, true);
});

test('a local token removal failure is not reported as successful cleanup', async () => {
  await assert.rejects(deleteAccountAndClear(async () => {}, async () => { throw new Error('secure storage unavailable'); }), /secure storage unavailable/);
});

// Exercise the actual shared API client, using the already installed TypeScript compiler.
function apiClient(fetch) {
  const source = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
  const module = { exports: {} };
  runInNewContext(compiled.outputText, { exports: module.exports, module, process: { env: { EXPO_PUBLIC_API_BASE_URL: 'https://api.example.test/api/v1' } }, fetch, AbortController, setTimeout, clearTimeout });
  return module.exports;
}

test('429 uses the normal API error shape without refreshing tokens or retrying automatically', async () => {
  let requests = 0, cleared = false;
  const client = apiClient(async () => { requests++; return new Response('gateway response', { status: 429, headers: { 'Retry-After': '60' } }); });
  client.configureApiTokens({ get: () => ({ accessToken: 'access', refreshToken: 'refresh' }), save: async () => {}, clear: async () => { cleared = true; } });
  await assert.rejects(client.api('/users/me', { method: 'DELETE' }), error => error.status === 429 && error.code === 'RATE_LIMIT_EXCEEDED' && error.message.includes('잠시 후'));
  assert.equal(requests, 1); assert.equal(cleared, false);
});

test('DELETE uses the current JWT and never includes tokens in its body or URL', async () => {
  const calls = [];
  const client = apiClient(async (url, init) => { calls.push({ url, init }); return new Response(null, { status: 204 }); });
  client.configureApiTokens({ get: () => ({ accessToken: 'access', refreshToken: 'refresh' }), save: async () => {}, clear: async () => {} });
  await deleteAccountAndClear(() => client.api('/users/me', { method: 'DELETE' }), async () => {});
  assert.equal(calls[0].url, 'https://api.example.test/api/v1/users/me');
  assert.equal(calls[0].init.method, 'DELETE'); assert.equal(calls[0].init.headers.Authorization, 'Bearer access');
  assert.equal(calls[0].init.body, undefined);
});

test('expired access token refreshes through the existing client before retrying DELETE', async () => {
  let tokens = { accessToken: 'old', refreshToken: 'refresh' }; const requests = [];
  const client = apiClient(async (url, init) => {
    requests.push({ url, init });
    if (url.endsWith('/auth/refresh')) return Response.json({ accessToken: 'new', refreshToken: 'rotated' });
    return new Response(null, { status: init.headers.Authorization === 'Bearer new' ? 204 : 401 });
  });
  client.configureApiTokens({ get: () => tokens, save: async next => { tokens = next; }, clear: async () => { tokens = null; } });
  await client.api('/users/me', { method: 'DELETE' });
  assert.equal(requests.length, 3); assert.equal(requests[2].init.headers.Authorization, 'Bearer new');
});

test('late refresh cannot restore authentication after account deletion clears the session', async () => {
  const pending = deferred(); const refreshStarted = deferred();
  let tokens = { accessToken: 'old', refreshToken: 'refresh' }, saved = 0;
  const client = apiClient(async url => {
    if (url.endsWith('/auth/refresh')) { refreshStarted.resolve(); return pending.promise; }
    return new Response(null, { status: 401 });
  });
  client.configureApiTokens({ get: () => tokens, save: async next => { saved++; tokens = next; }, clear: async () => { tokens = null; } });
  const request = client.api('/trips'); await refreshStarted.promise; tokens = null;
  pending.resolve(Response.json({ accessToken: 'late', refreshToken: 'late-refresh' }));
  await assert.rejects(request, error => error.status === 401);
  assert.equal(saved, 0); assert.equal(tokens, null);
});

test('retry after a lost deletion response signs out when the server rejects the deleted account', async () => {
  let cleared = false;
  const client = apiClient(async () => Response.json({ code: 'UNAUTHORIZED', message: 'Login required' }, { status: 401 }));
  client.configureApiTokens({ get: () => ({ accessToken: 'deleted', refreshToken: 'deleted' }), save: async () => {}, clear: async () => { cleared = true; } });
  await assert.rejects(client.api('/users/me', { method: 'DELETE' }), error => error.status === 401);
  assert.equal(cleared, true);
});
