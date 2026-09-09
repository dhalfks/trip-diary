import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '@/features/auth/auth-context';

export default function HomeScreen() {
  const { user, logout } = useAuth();
  return <SafeAreaView style={styles.safe}><View style={styles.container}>
    <Text style={styles.brand}>TRIP DIARY</Text><Text style={styles.title}>{user?.nickname}님,{`\n`}어디로 떠나볼까요?</Text>
    <Text style={styles.description}>여행 생성 화면은 다음 단계에서 연결됩니다.</Text><View style={styles.spacer} />
    <Text style={styles.email}>{user?.email}</Text><Pressable style={styles.logout} onPress={() => void logout()}><Text style={styles.logoutText}>로그아웃</Text></Pressable>
  </View></SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, container: { flex: 1, padding: 28 }, brand: { color: '#208AEF', fontWeight: '800', letterSpacing: 2, marginTop: 32 },
  title: { fontSize: 34, lineHeight: 44, fontWeight: '800', color: '#17212B', marginTop: 16 }, description: { color: '#65717E', fontSize: 16, marginTop: 12 },
  spacer: { flex: 1 }, email: { color: '#65717E', textAlign: 'center', marginBottom: 12 }, logout: { borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, padding: 15, alignItems: 'center' }, logoutText: { color: '#33404D', fontWeight: '700' },
});
