const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
export type TokenPair = { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number; refreshExpiresIn: number };
type Adapter = { get(): TokenPair | null; save(tokens: TokenPair): Promise<void>; clear(): Promise<void> };
let adapter: Adapter = { get: () => null, save: async () => {}, clear: async () => {} };
let refreshing: Promise<TokenPair> | null = null;

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: unknown[] = []) { super(message); this.name = 'ApiError'; }
}
export function configureApiTokens(value: Adapter) { adapter = value; }
export function jsonBody(body: unknown): RequestInit { return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }; }
export async function publicApi<T>(path: string, init?: RequestInit) { return parse<T>(await request(path, init)); }
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init, adapter.get()?.accessToken);
  if (response.status !== 401) return parse<T>(response);
  try { const tokens = await refresh(); return parse<T>(await request(path, init, tokens.accessToken)); }
  catch (error) {
    if (error instanceof ApiError && error.status === 401) await adapter.clear();
    throw error;
  }
}
async function refresh() {
  if (refreshing) return refreshing;
  const refreshToken = adapter.get()?.refreshToken;
  if (!refreshToken) throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.');
  refreshing = publicApi<TokenPair>('/auth/refresh', jsonBody({ refreshToken }))
    .then(async tokens => { await adapter.save(tokens); return tokens; }).finally(() => { refreshing = null; });
  return refreshing;
}
async function request(path: string, init?: RequestInit, token?: string) {
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 15000);
  try { return await fetch(`${BASE_URL}${path}`, { ...init, headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init?.headers }, signal: controller.signal }); }
  catch (error) { throw new Error(error instanceof Error && error.name === 'AbortError' ? '서버 응답 시간이 초과되었습니다.' : '서버에 연결할 수 없습니다. 네트워크와 API 주소를 확인해 주세요.'); }
  finally { clearTimeout(timeout); }
}
async function parse<T>(response: Response): Promise<T> {
  if (response.ok) return response.status === 204 ? undefined as T : response.json() as Promise<T>;
  const fallback = { code: 'UNKNOWN_ERROR', message: '요청을 처리하지 못했습니다.', fieldErrors: [] as unknown[] };
  const body = await response.json().catch(() => fallback) as typeof fallback;
  throw new ApiError(response.status, body.code ?? fallback.code, body.message ?? fallback.message, body.fieldErrors);
}
export function errorMessage(error: unknown) { return error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.'; }
