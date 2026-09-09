import { Link } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { AuthScreen } from '@/features/auth/auth-screen';
import { ErrorMessage } from '@/features/auth/error-message';
import { useAuth } from '@/features/auth/auth-context';
import { errorMessage } from '@/lib/api';

export default function SignupScreen() {
  const { signup } = useAuth(); const [email, setEmail] = useState(''); const [nickname, setNickname] = useState(''); const [password, setPassword] = useState('');
  const [error, setError] = useState<string>(); const [busy, setBusy] = useState(false);
  async function submit() {
    if (!email.trim() || !nickname.trim() || password.length < 8) return setError('이메일, 닉네임과 8자 이상의 비밀번호를 입력해 주세요.');
    setBusy(true); setError(undefined);
    try { await signup(email, password, nickname); } catch (reason) { setError(errorMessage(reason)); } finally { setBusy(false); }
  }
  return <AuthScreen title="여행 기록을 시작하세요" subtitle="계정을 만들면 여행이 안전하게 보관됩니다.">
    <ErrorMessage message={error} />
    <TextInput style={styles.input} value={email} onChangeText={setEmail} placeholder="이메일" autoCapitalize="none" keyboardType="email-address" autoComplete="email" editable={!busy} />
    <TextInput style={styles.input} value={nickname} onChangeText={setNickname} placeholder="닉네임" autoComplete="name" maxLength={40} editable={!busy} />
    <TextInput style={styles.input} value={password} onChangeText={setPassword} placeholder="비밀번호 (8자 이상)" secureTextEntry autoComplete="new-password" maxLength={72} editable={!busy} onSubmitEditing={() => void submit()} />
    <Pressable style={[styles.button, busy && styles.disabled]} onPress={() => void submit()} disabled={busy}><Text style={styles.buttonText}>{busy ? '가입 중…' : '회원가입'}</Text></Pressable>
    <View style={styles.footer}><Text>이미 계정이 있나요? </Text><Link href="/login" style={styles.link}>로그인</Link></View>
  </AuthScreen>;
}
const styles = StyleSheet.create({
  input: { height: 52, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, paddingHorizontal: 16, fontSize: 16, backgroundColor: '#FFF' },
  button: { height: 52, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: '#208AEF' }, buttonText: { color: '#FFF', fontWeight: '700', fontSize: 16 },
  disabled: { opacity: 0.55 }, footer: { flexDirection: 'row', justifyContent: 'center', marginTop: 4 }, link: { color: '#1677D2', fontWeight: '700' },
});
