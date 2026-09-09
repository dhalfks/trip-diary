import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import type { TokenPair } from '@/lib/api';

const KEY = 'trip-diary.auth.tokens.v1';
export async function loadTokens(): Promise<TokenPair | null> {
  const value = Platform.OS === 'web' ? globalThis.localStorage?.getItem(KEY) : await SecureStore.getItemAsync(KEY);
  if (!value) return null;
  try { return JSON.parse(value) as TokenPair; } catch { await clearTokens(); return null; }
}
export async function saveTokens(tokens: TokenPair) {
  const value = JSON.stringify(tokens);
  if (Platform.OS === 'web') globalThis.localStorage?.setItem(KEY, value);
  else await SecureStore.setItemAsync(KEY, value, { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY });
}
export async function clearTokens() {
  if (Platform.OS === 'web') globalThis.localStorage?.removeItem(KEY); else await SecureStore.deleteItemAsync(KEY);
}
