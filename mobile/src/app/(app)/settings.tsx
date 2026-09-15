import { router } from 'expo-router';
import { useMemo, useState, useSyncExternalStore } from 'react';
import { ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { accountMenu, createAccountDeletionStore, deletionWarning, openPolicy } from '@/features/account/account-model';
import { useAuth } from '@/features/auth/auth-context';

const menu = accountMenu(process.env.EXPO_PUBLIC_PRIVACY_POLICY_URL, process.env.EXPO_PUBLIC_TERMS_URL);

export default function SettingsScreen() {
  const { deleteAccount } = useAuth();
  const store = useMemo(() => createAccountDeletionStore(deleteAccount), [deleteAccount]);
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  const [notice, setNotice] = useState<string>();

  return <SafeAreaView style={styles.safe}>
    <View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel="이전 화면으로 돌아가기" hitSlop={10} disabled={state.busy} onPress={() => router.back()}><Text style={styles.link}>‹ 돌아가기</Text></Pressable><Text accessibilityRole="header" style={styles.heading}>설정</Text></View>
    <ScrollView contentContainerStyle={styles.content}>
      {menu.map(item => <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={item.label} disabled={state.busy}
        onPress={() => item.id === 'delete' ? store.request() : void openPolicy(item.url, Linking.openURL).then(setNotice)} style={styles.menu}>
        <Text style={item.id === 'delete' ? styles.danger : styles.label}>{item.label}</Text>
        <Text style={styles.help}>{item.id !== 'delete' && !item.url ? '준비 중' : '›'}</Text>
      </Pressable>)}
      {notice ? <Text accessibilityLiveRegion="polite" style={styles.help}>{notice}</Text> : null}
      {state.confirming ? <View style={styles.confirmation}>
        <Text style={styles.heading}>정말 탈퇴하시겠어요?</Text>
        <Text style={styles.description}>{deletionWarning}</Text>
        <Text style={styles.help}>진행 중인 사진 업로드를 멈춘 뒤 확인해 주세요.</Text>
        {state.error ? <Text accessibilityRole="alert" style={styles.danger}>{state.error}</Text> : null}
        <Pressable accessibilityRole="button" accessibilityLabel="모든 데이터를 삭제하고 회원 탈퇴" accessibilityState={{ disabled: state.busy, busy: state.busy }} disabled={state.busy} onPress={() => void store.confirm()} style={[styles.deleteButton, state.busy && styles.disabled]}>
          {state.busy ? <ActivityIndicator accessibilityLabel="회원 탈퇴 처리 중" color="#FFF" /> : null}<Text style={styles.deleteText}>{state.busy ? '탈퇴 처리 중…' : '모든 데이터를 삭제하고 탈퇴'}</Text>
        </Pressable>
        <Pressable accessibilityRole="button" disabled={state.busy} onPress={() => store.cancel()}><Text style={styles.cancel}>취소하고 계정 유지</Text></Pressable>
      </View> : null}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { padding: 20, flexDirection: 'row', alignItems: 'center', gap: 22, backgroundColor: '#FFF' },
  link: { color: '#1677D2', fontWeight: '700' }, heading: { fontSize: 20, color: '#17212B', fontWeight: '800' }, content: { padding: 22, gap: 14 },
  menu: { padding: 18, borderRadius: 12, backgroundColor: '#FFF', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { color: '#33404D', fontSize: 16, fontWeight: '700' }, help: { color: '#65717E', lineHeight: 22 }, danger: { color: '#B42318', fontWeight: '700', lineHeight: 24 },
  confirmation: { backgroundColor: '#FFF', padding: 20, borderWidth: 1, borderColor: '#F3CAC6', borderRadius: 12, gap: 18 }, description: { color: '#33404D', lineHeight: 25 },
  deleteButton: { backgroundColor: '#B42318', borderRadius: 10, padding: 16, alignItems: 'center', gap: 8 }, deleteText: { color: '#FFF', fontWeight: '800' },
  cancel: { color: '#1677D2', textAlign: 'center', padding: 12, fontWeight: '700' }, disabled: { opacity: 0.5 },
});
