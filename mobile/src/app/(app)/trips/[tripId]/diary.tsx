import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, AppState, FlatList, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router, useLocalSearchParams } from 'expo-router';
import { ScreenState } from '@/features/trips/screen-state';
import { diaryApi, itineraryApi, placeApi, tripApi } from '@/features/trips/trip-api';
import type { DiaryEntry, DiaryEntryInput, Itinerary, Place, Trip } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';
import { ImageUploadModal } from '@/features/images/image-upload-modal';
import { ImageGallery } from '@/features/images/image-gallery';

const topSafeAreaStyle = { flex: 1, backgroundColor: '#208AEF' } as const;
const bodyStyle = { flex: 1, backgroundColor: '#F7FAFC' } as const;
type Draft = DiaryEntryInput;

export default function DiaryScreen() {
  const params = useLocalSearchParams<{ tripId: string; dayId?: string }>();
  const tripId = params.tripId;
  const [trip, setTrip] = useState<Trip>();
  const [dayId, setDayId] = useState(params.dayId);
  const [entries, setEntries] = useState<DiaryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();
  const [editing, setEditing] = useState<DiaryEntry | null | undefined>(undefined);
  const [photoEntry, setPhotoEntry] = useState<DiaryEntry>();
  const [imageVersions, setImageVersions] = useState<Record<string, number>>({});
  const [imageRefresh, setImageRefresh] = useState(0);

  const loadTrip = useCallback(async () => {
    if (!tripId) return;
    try {
      const result = await tripApi.get(tripId);
      setTrip(result);
      setDayId(current => result.days.some(day => day.id === current) ? current : result.days[0]?.id);
      setError(undefined);
    } catch (reason) { setError(errorMessage(reason)); }
    finally { setLoading(false); }
  }, [tripId]);

  const loadEntries = useCallback(async () => {
    if (!tripId || !dayId) return;
    setRefreshing(true);
    try { setEntries(await diaryApi.list(tripId, dayId)); setImageRefresh(current => current + 1); setError(undefined); }
    catch (reason) { setError(errorMessage(reason)); }
    finally { setRefreshing(false); }
  }, [tripId, dayId]);

  useEffect(() => {
    // The loader updates state after its API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadTrip();
  }, [loadTrip]);
  useEffect(() => {
    // The loader updates state after its API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadEntries();
  }, [loadEntries]);

  async function remove(entry: DiaryEntry) {
    if (!tripId || !dayId) return;
    try { await diaryApi.remove(tripId, dayId, entry.id); await loadEntries(); }
    catch (reason) { Alert.alert('기록을 삭제하지 못했어요', errorMessage(reason)); }
  }

  function confirmDelete(entry: DiaryEntry) {
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.('이 기록을 삭제할까요?')) void remove(entry);
    } else {
      Alert.alert('기록 삭제', '삭제한 기록은 복구할 수 없어요.', [{ text: '취소' }, { text: '삭제', style: 'destructive', onPress: () => void remove(entry) }]);
    }
  }

  if (loading) return <SafeAreaView style={styles.safe}><ScreenState loading title="기록을 불러오는 중이에요" /></SafeAreaView>;
  if (!trip) return <SafeAreaView style={styles.safe}><ScreenState title="여행을 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => void loadTrip()} /></SafeAreaView>;
  const selectedDay = trip.days.find(day => day.id === dayId);

  return <SafeAreaView style={topSafeAreaStyle} edges={['top', 'left', 'right']}>
    <View style={styles.header}><Pressable onPress={() => router.back()}><Text style={styles.link}>‹ 여행</Text></Pressable><Text style={styles.headerTitle}>여행 기록</Text><Pressable disabled={!dayId} onPress={() => setEditing(null)}><Text style={styles.link}>＋ 작성</Text></Pressable></View>
    <Pressable style={{ paddingHorizontal: 22, paddingVertical: 12, backgroundColor: '#EAF4FE' }} onPress={() => router.push({ pathname: '/trips/[tripId]/diaries', params: { tripId: trip.id } })}><Text style={styles.link}>여행 다이어리 만들기 · 미리보기 ›</Text></Pressable>
    <FlatList style={bodyStyle} data={entries} keyExtractor={item => item.id} refreshing={refreshing} onRefresh={() => void loadEntries()} contentContainerStyle={styles.content}
      ListHeaderComponent={<><Text style={styles.tripTitle}>{trip.title}</Text><ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.days}>{trip.days.map(day => <Pressable key={day.id} onPress={() => setDayId(day.id)} style={[styles.day, day.id === dayId && styles.selected]}><Text style={[styles.dayLabel, day.id === dayId && styles.white]}>DAY {day.dayNumber}</Text><Text style={[styles.dayDate, day.id === dayId && styles.white]}>{day.date.slice(5)}</Text></Pressable>)}</ScrollView><View style={styles.sectionRow}><View><Text style={styles.section}>날짜별 기록</Text><Text style={styles.date}>{selectedDay?.date}</Text></View><Pressable style={styles.addButton} onPress={() => setEditing(null)}><Text style={styles.addText}>＋ 기록</Text></Pressable></View>{error ? <Pressable onPress={() => void loadEntries()}><Text style={styles.error}>{error} · 다시 시도</Text></Pressable> : null}</>}
      ListEmptyComponent={!refreshing && !error ? <View style={styles.empty}><Text style={styles.emptyTitle}>아직 기록이 없어요</Text><Text style={styles.emptyText}>오늘의 기억을 잊기 전에 남겨 보세요.</Text></View> : null}
      renderItem={({ item }) => <View style={styles.card}><Pressable accessibilityRole="button" accessibilityLabel={`${item.title} 기록 수정`} onPress={() => setEditing(item)}><View style={styles.cardTop}><Text style={styles.cardTitle}>{item.title}</Text><Pressable accessibilityRole="button" accessibilityLabel={`${item.title} 기록 삭제`} onPress={event => { event.stopPropagation(); confirmDelete(item); }}><Text style={styles.delete}>삭제</Text></Pressable></View><Text numberOfLines={3} style={styles.preview}>{item.content}</Text><View style={styles.tags}>{item.itinerary ? <Text style={styles.tag}>일정 · {item.itinerary.name}</Text> : null}{item.place ? <Text style={styles.tag}>장소 · {item.place.name}</Text> : null}</View><Text style={styles.updated}>{new Date(item.updatedAt).toLocaleString()}</Text></Pressable><ImageGallery target={{ tripId: trip.id, dayId: item.tripDayId, entryId: item.id }} refreshKey={`${imageRefresh}:${imageVersions[item.id] ?? 0}`} /><Pressable accessibilityRole="button" accessibilityLabel={`${item.title}에 사진 추가`} onPress={() => setPhotoEntry(item)}><Text style={styles.photoLink}>＋ 사진 추가</Text></Pressable></View>} />
    {editing !== undefined && dayId ? <DiaryEditor tripId={trip.id} dayId={dayId} entry={editing} onClose={() => setEditing(undefined)} onSaved={async () => { setEditing(undefined); await loadEntries(); }} /> : null}
    {photoEntry ? <ImageUploadModal key={photoEntry.id} target={{ tripId: trip.id, dayId: photoEntry.tripDayId, entryId: photoEntry.id }} title={photoEntry.title} onClose={() => setPhotoEntry(undefined)} onUploaded={() => setImageVersions(current => ({ ...current, [photoEntry.id]: (current[photoEntry.id] ?? 0) + 1 }))} /> : null}
  </SafeAreaView>;
}

function DiaryEditor({ tripId, dayId, entry, onClose, onSaved }: { tripId: string; dayId: string; entry: DiaryEntry | null; onClose(): void; onSaved(): Promise<void> }) {
  const initial = useMemo<Draft>(() => ({ title: entry?.title ?? '', content: entry?.content ?? '', itineraryId: entry?.itinerary?.id ?? null, placeId: entry?.place?.id ?? null }), [entry]);
  const [draft, setDraft] = useState<Draft>(initial);
  const [itineraries, setItineraries] = useState<Itinerary[]>([]);
  const [places, setPlaces] = useState<Place[]>([]);
  const [restored, setRestored] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const savedRef = useRef(false);
  const draftKey = `trip-diary:draft:${tripId}:${dayId}:${entry?.id ?? 'new'}`;
  const dirty = JSON.stringify(draft) !== JSON.stringify(initial);

  useEffect(() => {
    let active = true;
    Promise.all([AsyncStorage.getItem(draftKey), itineraryApi.list(tripId, dayId), placeApi.list(tripId)])
      .then(([stored, itineraryList, placeList]) => {
        if (!active) return;
        setItineraries(itineraryList); setPlaces(placeList);
        if (stored) { setDraft(JSON.parse(stored) as Draft); setRestored(true); }
      }).catch(reason => { if (active) setError(errorMessage(reason)); });
    return () => { active = false; };
  }, [dayId, draftKey, tripId]);

  useEffect(() => {
    if (!dirty) return;
    const timer = setTimeout(() => { void AsyncStorage.setItem(draftKey, JSON.stringify(draft)); }, 500);
    return () => clearTimeout(timer);
  }, [draft, draftKey, dirty]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state !== 'active') void AsyncStorage.setItem(draftKey, JSON.stringify(draft));
    });
    return () => subscription.remove();
  }, [draft, draftKey]);

  async function persistAndClose() {
    await AsyncStorage.setItem(draftKey, JSON.stringify(draft));
    onClose();
  }

  function close() {
    if (!dirty || savedRef.current) return onClose();
    Alert.alert('작성 중인 내용이 있어요', '초안은 이 기기에 자동 저장됩니다.', [{ text: '계속 작성', style: 'cancel' }, { text: '닫기', onPress: () => void persistAndClose() }]);
  }

  async function discardDraft() {
    await AsyncStorage.removeItem(draftKey);
    setDraft(initial); setRestored(false);
  }

  async function save() {
    if (!draft.title.trim()) return setError('제목을 입력해 주세요.');
    if (!draft.content.trim()) return setError('기록 내용을 입력해 주세요.');
    const input: DiaryEntryInput = { ...draft, title: draft.title.trim(), content: draft.content.trim() };
    setBusy(true); setError(undefined);
    try {
      if (entry) await diaryApi.update(tripId, dayId, entry.id, input); else await diaryApi.create(tripId, dayId, input);
      savedRef.current = true;
      await AsyncStorage.removeItem(draftKey);
      await onSaved();
    } catch (reason) { setError(errorMessage(reason)); }
    finally { setBusy(false); }
  }

  return <Modal visible animationType="slide" onRequestClose={close}><SafeAreaView style={topSafeAreaStyle} edges={['top', 'left', 'right']}><View style={styles.header}><Pressable onPress={close}><Text style={styles.link}>취소</Text></Pressable><Text style={styles.headerTitle}>{entry ? '기록 수정' : '기록 작성'}</Text><Pressable disabled={busy} onPress={() => void save()}><Text style={styles.link}>{busy ? '저장 중' : '저장'}</Text></Pressable></View><ScrollView style={bodyStyle} keyboardShouldPersistTaps="handled" contentContainerStyle={styles.form}>
    {restored ? <View style={styles.restored}><Text style={styles.restoredText}>저장된 초안을 복구했어요.</Text><Pressable onPress={() => void discardDraft()}><Text style={styles.delete}>초안 삭제</Text></Pressable></View> : <Text style={styles.autoSave}>입력 내용은 이 기기에 자동 저장됩니다.</Text>}
    <Text style={styles.label}>제목</Text><TextInput style={styles.input} value={draft.title} onChangeText={title => setDraft(current => ({ ...current, title }))} maxLength={120} placeholder="오늘의 기억을 한 줄로 남겨 보세요" />
    <Text style={styles.label}>내용</Text><TextInput style={[styles.input, styles.contentInput]} value={draft.content} onChangeText={content => setDraft(current => ({ ...current, content }))} maxLength={20000} multiline textAlignVertical="top" placeholder="여행 중 느낀 점과 기억을 자유롭게 기록하세요." />
    {!entry ? <Text style={styles.autoSave}>기록을 저장한 뒤 목록의 ‘사진 추가’를 눌러 주세요.</Text> : null}
    <Text style={styles.label}>일정 연결 (선택)</Text><ScrollView horizontal contentContainerStyle={styles.options}><Choice label="연결 안 함" selected={!draft.itineraryId} onPress={() => setDraft(current => ({ ...current, itineraryId: null }))} />{itineraries.map(item => <Choice key={item.id} label={item.title} selected={draft.itineraryId === item.id} onPress={() => setDraft(current => ({ ...current, itineraryId: item.id }))} />)}</ScrollView>
    <Text style={styles.label}>장소 연결 (선택)</Text><ScrollView horizontal contentContainerStyle={styles.options}><Choice label="연결 안 함" selected={!draft.placeId} onPress={() => setDraft(current => ({ ...current, placeId: null }))} />{places.map(place => <Choice key={place.id} label={place.name} selected={draft.placeId === place.id} onPress={() => setDraft(current => ({ ...current, placeId: place.id }))} />)}</ScrollView>
    {error ? <Text style={styles.error}>{error}</Text> : null}
  </ScrollView></SafeAreaView></Modal>;
}

function Choice({ label, selected, onPress }: { label: string; selected: boolean; onPress(): void }) {
  return <Pressable onPress={onPress} style={[styles.choice, selected && styles.selected]}><Text style={selected ? styles.white : styles.choiceText}>{label}</Text></Pressable>;
}

const styles = StyleSheet.create({
  photoLink: { color: '#1677D2', fontWeight: '700', paddingTop: 14, paddingBottom: 4 },
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { height: 58, paddingHorizontal: 18, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, headerTitle: { color: '#17212B', fontSize: 18, fontWeight: '800' }, link: { color: '#1677D2', fontWeight: '800' }, content: { padding: 22, paddingBottom: 60 }, tripTitle: { color: '#17212B', fontSize: 27, fontWeight: '900' }, days: { gap: 9, paddingTop: 16 }, day: { borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, padding: 11, alignItems: 'center', backgroundColor: '#FFF' }, selected: { backgroundColor: '#208AEF', borderColor: '#208AEF' }, dayLabel: { color: '#65717E', fontSize: 11, fontWeight: '800' }, dayDate: { color: '#17212B', fontWeight: '800', marginTop: 3 }, white: { color: '#FFF' }, sectionRow: { marginTop: 27, marginBottom: 14, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' }, section: { color: '#17212B', fontSize: 18, fontWeight: '800' }, date: { color: '#7A8794', marginTop: 3 }, addButton: { backgroundColor: '#208AEF', borderRadius: 11, paddingHorizontal: 15, paddingVertical: 10 }, addText: { color: '#FFF', fontWeight: '800' }, empty: { alignItems: 'center', paddingVertical: 45, backgroundColor: '#FFF', borderRadius: 16 }, emptyTitle: { color: '#33404D', fontWeight: '800' }, emptyText: { color: '#8B96A1', marginTop: 6 }, card: { padding: 17, marginBottom: 11, backgroundColor: '#FFF', borderWidth: 1, borderColor: '#E7EBEF', borderRadius: 15 }, cardTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 }, cardTitle: { flex: 1, color: '#17212B', fontSize: 17, fontWeight: '800' }, preview: { color: '#526273', lineHeight: 21, marginTop: 9 }, tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginTop: 11 }, tag: { color: '#1677D2', backgroundColor: '#EAF4FE', borderRadius: 12, paddingHorizontal: 9, paddingVertical: 5, fontSize: 12, fontWeight: '700' }, updated: { color: '#98A2AD', fontSize: 11, marginTop: 10 }, delete: { color: '#D92D20', fontWeight: '700' }, form: { padding: 22, paddingBottom: 60, gap: 9 }, autoSave: { color: '#65717E', fontSize: 13, marginBottom: 5 }, restored: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#EAF4FE', borderRadius: 10, padding: 12 }, restoredText: { color: '#1677D2', fontWeight: '700' }, label: { color: '#33404D', fontWeight: '700', marginTop: 9 }, input: { minHeight: 50, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 11, paddingHorizontal: 13, backgroundColor: '#FFF', fontSize: 15 }, contentInput: { minHeight: 260, paddingTop: 13 }, options: { gap: 8 }, choice: { borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 20, paddingHorizontal: 13, paddingVertical: 9, backgroundColor: '#FFF' }, choiceText: { color: '#33404D' }, error: { color: '#B42318', backgroundColor: '#FFF0F0', padding: 11, borderRadius: 9 },
});
