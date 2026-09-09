import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, configureApiTokens, jsonBody, publicApi, type TokenPair } from '@/lib/api';
import { clearTokens, loadTokens, saveTokens } from '@/lib/token-storage';

type User = { id: string; email: string; nickname: string; status: string; createdAt: string; updatedAt: string };
type AuthValue = {
  initialized: boolean; isAuthenticated: boolean; user: User | null;
  login(email: string, password: string): Promise<void>;
  signup(email: string, password: string, nickname: string): Promise<void>;
  logout(): Promise<void>;
};
const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const tokenRef = useRef<TokenPair | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [initialized, setInitialized] = useState(false);
  const persist = useCallback(async (tokens: TokenPair) => { await saveTokens(tokens); tokenRef.current = tokens; }, []);
  const clear = useCallback(async () => { await clearTokens(); tokenRef.current = null; setUser(null); }, []);

  useEffect(() => { configureApiTokens({ get: () => tokenRef.current, save: persist, clear }); }, [persist, clear]);
  const me = useCallback(async () => setUser(await api<User>('/users/me')), []);

  useEffect(() => {
    let mounted = true;
    void (async () => {
      const stored = await loadTokens();
      if (!mounted) return;
      tokenRef.current = stored;
      configureApiTokens({ get: () => tokenRef.current, save: persist, clear });
      if (stored) try { await me(); } catch { /* Keep persisted tokens during temporary network failures. */ }
      if (mounted) setInitialized(true);
    })();
    return () => { mounted = false; };
  }, [persist, clear, me]);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await publicApi<TokenPair>('/auth/login', jsonBody({ email: email.trim(), password }));
    await persist(tokens); await me();
  }, [persist, me]);
  const signup = useCallback(async (email: string, password: string, nickname: string) => {
    await publicApi('/auth/signup', jsonBody({ email: email.trim(), password, nickname: nickname.trim() }));
    await login(email, password);
  }, [login]);
  const logout = useCallback(async () => {
    const refreshToken = tokenRef.current?.refreshToken;
    try { if (refreshToken) await publicApi('/auth/logout', jsonBody({ refreshToken })); } finally { await clear(); }
  }, [clear]);

  const value = useMemo(() => ({ initialized, isAuthenticated: user !== null, user, login, signup, logout }), [initialized, user, login, signup, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
