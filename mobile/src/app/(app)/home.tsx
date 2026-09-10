import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useAuth } from '@/features/auth/auth-context';
import { ScreenState } from '@/features/trips/screen-state';
import { tripApi } from '@/features/trips/trip-api';
import type { Trip } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';

export default function HomeScreen() {
  const { user, logout } = useAuth();
  const [trips, setTrips] = useState<Trip[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string>();

  const load = useCallback(async (nextPage = 0, append = false) => {
    try {
      const result = await tripApi.list(nextPage);
      setTrips(current => append ? [...current, ...result.content.filter(item => !current.some(saved => saved.id === item.id))] : result.content);
      setPage(result.page); setLast(result.last); setError(undefined);
    } catch (reason) { setError(errorMessage(reason)); }
  }, []);

  useEffect(() => {
    // The loader updates state only after its awaited API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load().finally(() => setLoading(false));
  }, [load]);
  async function refresh() { setRefreshing(true); await load(); setRefreshing(false); }
  async function loadMore() {
    if (last || loadingMore || loading || error) return;
    setLoadingMore(true); await load(page + 1, true); setLoadingMore(false);
  }

  if (loading) return <SafeAreaView style={styles.safe}><ScreenState loading title="여행을 불러오는 중이에요" /></SafeAreaView>;
  if (error && trips.length === 0) return <SafeAreaView style={styles.safe}><ScreenState title="여행을 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => { setLoading(true); void load().finally(() => setLoading(false)); }} /></SafeAreaView>;

  return <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
    <View style={styles.header}>
      <View><Text style={styles.brand}>TRIP DIARY</Text><Text style={styles.greeting}>{user?.nickname}님의 여행</Text></View>
      <Pressable onPress={() => void logout()} hitSlop={10}><Text style={styles.logout}>로그아웃</Text></Pressable>
    </View>
    <FlatList data={trips} keyExtractor={item => item.id}
      contentContainerStyle={[styles.list, trips.length === 0 && styles.emptyList]}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void refresh()} tintColor="#208AEF" />}
      onEndReached={() => void loadMore()} onEndReachedThreshold={0.35}
      ListEmptyComponent={<ScreenState title="아직 여행이 없어요" description="첫 여행을 만들고 날짜별 기록을 시작해 보세요." actionLabel="첫 여행 만들기" onAction={() => router.push('/trips/new')} />}
      ListHeaderComponent={trips.length ? <Text style={styles.count}>총 {trips.length}개의 여행</Text> : null}
      ListFooterComponent={loadingMore ? <ActivityIndicator color="#208AEF" style={styles.footerLoader} /> : error ? <Pressable onPress={() => void loadMore()}><Text style={styles.moreError}>{error} · 다시 시도</Text></Pressable> : null}
      renderItem={({ item }) => <Pressable style={({ pressed }) => [styles.card, pressed && styles.pressed]} onPress={() => router.push({ pathname: '/trips/[tripId]', params: { tripId: item.id } })}>
        <View style={styles.cardTop}><Text numberOfLines={1} style={styles.cardTitle}>{item.title}</Text><Text style={styles.chevron}>›</Text></View>
        <Text style={styles.period}>{item.startDate.replace(/-/g, '.')} — {item.endDate.replace(/-/g, '.')}</Text>
        <View style={styles.meta}><Text style={styles.badge}>{item.days.length}일</Text><Text style={styles.timezone}>{item.timezone}</Text></View>
      </Pressable>}
    />
    {trips.length ? <Pressable style={styles.fab} onPress={() => router.push('/trips/new')}><Text style={styles.fabText}>＋ 새 여행</Text></Pressable> : null}
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { paddingHorizontal: 24, paddingVertical: 18, backgroundColor: '#FFF', borderBottomColor: '#E7EBEF', borderBottomWidth: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { color: '#208AEF', fontSize: 12, fontWeight: '900', letterSpacing: 1.8 }, greeting: { color: '#17212B', fontSize: 25, fontWeight: '800', marginTop: 5 }, logout: { color: '#65717E', fontWeight: '700' },
  list: { padding: 20, paddingBottom: 110 }, emptyList: { flexGrow: 1 }, count: { color: '#65717E', fontSize: 14, marginBottom: 12 },
  card: { backgroundColor: '#FFF', borderRadius: 18, padding: 20, marginBottom: 14, borderWidth: 1, borderColor: '#E7EBEF', shadowColor: '#17212B', shadowOpacity: 0.05, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 2 },
  pressed: { opacity: 0.72 }, cardTop: { flexDirection: 'row', alignItems: 'center' }, cardTitle: { flex: 1, color: '#17212B', fontSize: 20, fontWeight: '800' }, chevron: { color: '#A0A9B2', fontSize: 30 },
  period: { color: '#4E5D6C', fontSize: 15, marginTop: 10 }, meta: { flexDirection: 'row', alignItems: 'center', marginTop: 14, gap: 10 }, badge: { color: '#1677D2', backgroundColor: '#EAF4FE', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 20, fontWeight: '700' }, timezone: { color: '#7A8794', fontSize: 13 },
  fab: { position: 'absolute', right: 22, bottom: 26, backgroundColor: '#208AEF', borderRadius: 28, paddingHorizontal: 20, height: 54, alignItems: 'center', justifyContent: 'center', elevation: 5, shadowColor: '#0B5FA5', shadowOpacity: 0.28, shadowRadius: 9, shadowOffset: { width: 0, height: 5 } }, fabText: { color: '#FFF', fontSize: 16, fontWeight: '800' },
  footerLoader: { padding: 18 }, moreError: { color: '#B42318', textAlign: 'center', padding: 16 },
});
