import { Link } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { AuthScreen } from '@/features/auth/auth-screen';
import { ErrorMessage } from '@/features/auth/error-message';
import { useAuth } from '@/features/auth/auth-context';
import { errorMessage } from '@/lib/api';

export default function LoginScreen() {
  const { login } = useAuth(); const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [error, setError] = useState<string>(); const [busy, setBusy] = useState(false);
  async function submit() {
    if (!email.trim() || !password) return setError('이메일과 비밀번호를 입력해 주세요.');
    setBusy(true); setError(undefined);
    try { await login(email, password); } catch (reason) { setError(errorMessage(reason)); } finally { setBusy(false); }
  }
  return <AuthScreen title="다시 떠날 준비가 됐나요?" subtitle="Trip Diary에 로그인하세요.">
    <ErrorMessage message={error} />
    <TextInput style={styles.input} value={email} onChangeText={setEmail} placeholder="이메일" autoCapitalize="none" keyboardType="email-address" autoComplete="email" editable={!busy} />
    <TextInput style={styles.input} value={password} onChangeText={setPassword} placeholder="비밀번호" secureTextEntry autoComplete="current-password" editable={!busy} onSubmitEditing={() => void submit()} />
    <Pressable style={[styles.button, busy && styles.disabled]} onPress={() => void submit()} disabled={busy}><Text style={styles.buttonText}>{busy ? '로그인 중…' : '로그인'}</Text></Pressable>
    <View style={styles.footer}><Text>처음이신가요? </Text><Link href="/signup" style={styles.link}>회원가입</Link></View>
  </AuthScreen>;
}
const styles = StyleSheet.create({
  input: { height: 52, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, paddingHorizontal: 16, fontSize: 16, backgroundColor: '#FFF' },
  button: { height: 52, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: '#208AEF' }, buttonText: { color: '#FFF', fontWeight: '700', fontSize: 16 },
  disabled: { opacity: 0.55 }, footer: { flexDirection: 'row', justifyContent: 'center', marginTop: 4 }, link: { color: '#1677D2', fontWeight: '700' },
});
