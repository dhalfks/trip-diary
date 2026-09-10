import { useCallback, useEffect, useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router, useLocalSearchParams } from 'expo-router';
import { ScreenState } from '@/features/trips/screen-state';
import { tripApi } from '@/features/trips/trip-api';
import type { Trip } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';

export default function TripDetailScreen() {
  const { tripId } = useLocalSearchParams<{ tripId: string }>();
  const [trip, setTrip] = useState<Trip>(); const [selectedDayId, setSelectedDayId] = useState<string>();
  const [loading, setLoading] = useState(true); const [error, setError] = useState<string>(); const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    if (!tripId) { setError('여행 식별자가 올바르지 않습니다.'); setLoading(false); return; }
    try {
      const result = await tripApi.get(tripId); setTrip(result);
      setSelectedDayId(current => result.days.some(day => day.id === current) ? current : result.days[0]?.id); setError(undefined);
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }, [tripId]);
  useEffect(() => {
    // The loader updates state only after its awaited API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  async function remove() {
    if (!trip || deleting) return;
    setDeleting(true);
    try { await tripApi.remove(trip.id); router.replace('/home'); }
    catch (reason) { Alert.alert('삭제하지 못했어요', errorMessage(reason)); }
    finally { setDeleting(false); }
  }
  function confirmDelete() {
    if (Platform.OS === 'web') { if (globalThis.confirm?.('이 여행과 날짜 정보를 삭제할까요?')) void remove(); return; }
    Alert.alert('여행 삭제', '이 여행과 날짜 정보를 삭제할까요?', [
      { text: '취소', style: 'cancel' }, { text: '삭제', style: 'destructive', onPress: () => void remove() },
    ]);
  }

  if (loading) return <SafeAreaView style={styles.safe}><ScreenState loading title="여행을 불러오는 중이에요" /></SafeAreaView>;
  if (error || !trip) return <SafeAreaView style={styles.safe}><ScreenState title="여행을 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => { setLoading(true); void load(); }} /></SafeAreaView>;
  const selectedDay = trip.days.find(day => day.id === selectedDayId) ?? trip.days[0];

  return <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
    <View style={styles.header}>
      <Pressable onPress={() => router.replace('/home')} hitSlop={12}><Text style={styles.back}>‹ 목록</Text></Pressable>
      <View style={styles.actions}>
        <Pressable onPress={() => router.push({ pathname: '/trips/[tripId]/edit', params: { tripId: trip.id } })}><Text style={styles.edit}>수정</Text></Pressable>
        <Pressable disabled={deleting} onPress={confirmDelete}><Text style={styles.delete}>{deleting ? '삭제 중' : '삭제'}</Text></Pressable>
      </View>
    </View>
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.eyebrow}>{trip.timezone}</Text><Text style={styles.title}>{trip.title}</Text>
      <Text style={styles.period}>{trip.startDate.replace(/-/g, '.')} — {trip.endDate.replace(/-/g, '.')}</Text>
      <Text style={styles.sectionTitle}>여행 날짜</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.days}>
        {trip.days.map(day => {
          const selected = day.id === selectedDay?.id;
          return <Pressable key={day.id} onPress={() => setSelectedDayId(day.id)} style={[styles.day, selected && styles.selectedDay]}>
            <Text style={[styles.dayNumber, selected && styles.selectedText]}>DAY {day.dayNumber}</Text>
            <Text style={[styles.dayDate, selected && styles.selectedText]}>{day.date.slice(5).replace('-', '.')}</Text>
          </Pressable>;
        })}
      </ScrollView>
      {selectedDay ? <View style={styles.dayPanel}>
        <Text style={styles.panelTitle}>DAY {selectedDay.dayNumber}</Text><Text style={styles.panelDate}>{selectedDay.date}</Text>
        <Text style={styles.emptyTitle}>아직 등록된 일정이 없어요</Text><Text style={styles.emptyDescription}>날짜별 일정 관리는 다음 단계에서 연결됩니다.</Text>
      </View> : null}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { height: 58, paddingHorizontal: 20, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { color: '#1677D2', fontSize: 16, fontWeight: '700' }, actions: { flexDirection: 'row', gap: 20 }, edit: { color: '#1677D2', fontWeight: '800' }, delete: { color: '#D92D20', fontWeight: '800' },
  content: { padding: 24, paddingBottom: 48 }, eyebrow: { color: '#208AEF', fontSize: 13, fontWeight: '800' }, title: { color: '#17212B', fontSize: 32, fontWeight: '900', marginTop: 8 }, period: { color: '#65717E', fontSize: 16, marginTop: 10 },
  sectionTitle: { color: '#17212B', fontSize: 19, fontWeight: '800', marginTop: 34, marginBottom: 14 }, days: { gap: 10, paddingRight: 24 },
  day: { minWidth: 78, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 14, backgroundColor: '#FFF', paddingHorizontal: 14, paddingVertical: 12, alignItems: 'center' }, selectedDay: { borderColor: '#208AEF', backgroundColor: '#208AEF' },
  dayNumber: { color: '#65717E', fontSize: 11, fontWeight: '800' }, dayDate: { color: '#17212B', fontSize: 16, fontWeight: '800', marginTop: 4 }, selectedText: { color: '#FFF' },
  dayPanel: { marginTop: 22, minHeight: 220, borderWidth: 1, borderColor: '#E7EBEF', borderRadius: 18, backgroundColor: '#FFF', padding: 20 }, panelTitle: { color: '#208AEF', fontWeight: '900' }, panelDate: { color: '#33404D', fontSize: 18, fontWeight: '800', marginTop: 5 },
  emptyTitle: { color: '#4E5D6C', fontWeight: '700', textAlign: 'center', marginTop: 50 }, emptyDescription: { color: '#8B96A1', fontSize: 13, textAlign: 'center', marginTop: 7 },
});
