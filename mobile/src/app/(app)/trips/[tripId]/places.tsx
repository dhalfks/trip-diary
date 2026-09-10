import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router, useLocalSearchParams } from 'expo-router';
import { ScreenState } from '@/features/trips/screen-state';
import { placeApi } from '@/features/trips/trip-api';
import type { Place, PlaceInput, PlaceSearchResult } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';

const topSafeAreaStyle = { flex: 1, backgroundColor: '#208AEF' } as const;
const contentBackgroundStyle = { flex: 1, backgroundColor: '#F7FAFC' } as const;

export default function PlacesScreen() {
  const { tripId } = useLocalSearchParams<{ tripId: string }>();
  const [places, setPlaces] = useState<Place[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState<string>();
  const [editing, setEditing] = useState<Place | null | undefined>(undefined);
  const load = useCallback(async () => { if (!tripId) return; try { setPlaces(await placeApi.list(tripId)); setError(undefined); } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); } }, [tripId]);
  useEffect(() => {
    // The loader updates state after its API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);
  async function remove(place: Place) {
    if (!tripId) return;
    try { await placeApi.remove(tripId, place.id); await load(); } catch (reason) { Alert.alert('장소를 삭제하지 못했어요', errorMessage(reason)); }
  }
  if (loading) return <SafeAreaView style={styles.safe}><ScreenState loading title="장소를 불러오는 중이에요" /></SafeAreaView>;
  return <SafeAreaView style={topSafeAreaStyle} edges={['top', 'left', 'right']}>
    <View style={styles.header}><Pressable onPress={() => router.back()}><Text style={styles.back}>‹ 여행</Text></Pressable><Text style={styles.headerTitle}>장소 관리</Text><Pressable onPress={() => setEditing(null)}><Text style={styles.add}>＋ 추가</Text></Pressable></View>
    {error && places.length === 0 ? <ScreenState title="장소를 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => void load()} /> :
      <FlatList style={contentBackgroundStyle} data={places} keyExtractor={item => item.id} contentContainerStyle={[styles.list, !places.length && styles.grow]}
        ListEmptyComponent={<ScreenState title="저장된 장소가 없어요" description="일정에서 사용할 장소를 직접 등록해 보세요." actionLabel="장소 추가" onAction={() => setEditing(null)} />}
        renderItem={({ item }) => <Pressable onPress={() => setEditing(item)} style={styles.card}>
          <View style={styles.cardText}><Text style={styles.name}>{item.name}</Text><Text style={styles.address}>{item.address || '주소 없음'}</Text>{item.latitude != null ? <Text style={styles.coords}>{item.latitude}, {item.longitude}</Text> : null}</View>
          <Pressable hitSlop={10} onPress={() => Alert.alert('장소 삭제', `${item.name}을(를) 삭제할까요?`, [{ text: '취소' }, { text: '삭제', style: 'destructive', onPress: () => void remove(item) }])}><Text style={styles.delete}>삭제</Text></Pressable>
        </Pressable>} />}
    {editing !== undefined ? <PlaceForm tripId={tripId} place={editing} onClose={() => setEditing(undefined)} onSaved={async () => { setEditing(undefined); await load(); }} /> : null}
  </SafeAreaView>;
}

function PlaceForm({ tripId, place, onClose, onSaved }: { tripId: string; place: Place | null; onClose(): void; onSaved(): Promise<void> }) {
  const [name, setName] = useState(place?.name ?? ''); const [address, setAddress] = useState(place?.address ?? '');
  const [latitude, setLatitude] = useState(place?.latitude?.toString() ?? ''); const [longitude, setLongitude] = useState(place?.longitude?.toString() ?? '');
  const [results, setResults] = useState<PlaceSearchResult[]>([]); const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string>(); const [busy, setBusy] = useState(false); const [searching, setSearching] = useState(false);
  async function search() {
    const query = name.trim();
    if (query.length < 2) return setError('검색할 장소 이름을 두 글자 이상 입력해 주세요.');
    setSearching(true); setError(undefined);
    try { setResults(await placeApi.search(tripId, query)); setSearched(true); }
    catch (reason) { setResults([]); setSearched(false); setError(errorMessage(reason)); }
    finally { setSearching(false); }
  }
  function selectResult(result: PlaceSearchResult) {
    setName(result.name); setAddress(result.address);
    setLatitude(String(result.latitude)); setLongitude(String(result.longitude));
    setResults([]); setSearched(false); setError(undefined);
  }
  async function save() {
    if (!name.trim()) return setError('장소 이름을 입력해 주세요.');
    if (!!latitude.trim() !== !!longitude.trim()) return setError('위도와 경도는 함께 입력해 주세요.');
    const lat = latitude.trim() ? Number(latitude) : null; const lng = longitude.trim() ? Number(longitude) : null;
    if ((lat != null && (!Number.isFinite(lat) || lat < -90 || lat > 90)) || (lng != null && (!Number.isFinite(lng) || lng < -180 || lng > 180))) return setError('좌표 범위를 확인해 주세요.');
    const input: PlaceInput = { name: name.trim(), address: address.trim() || null, latitude: lat, longitude: lng };
    setBusy(true); setError(undefined);
    try { if (place) await placeApi.update(tripId, place.id, input); else await placeApi.create(tripId, input); await onSaved(); } catch (reason) { setError(errorMessage(reason)); } finally { setBusy(false); }
  }
  return <Modal animationType="slide" visible onRequestClose={onClose}><SafeAreaView style={topSafeAreaStyle} edges={['top', 'left', 'right']}>
    <View style={styles.header}><Pressable onPress={onClose}><Text style={styles.back}>취소</Text></Pressable><Text style={styles.headerTitle}>{place ? '장소 수정' : '장소 추가'}</Text><Pressable disabled={busy} onPress={() => void save()}><Text style={styles.add}>{busy ? '저장 중' : '저장'}</Text></Pressable></View>
    <ScrollView style={contentBackgroundStyle} contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled"><Text style={styles.label}>장소 이름</Text><View style={styles.searchRow}><TextInput style={[styles.input, styles.searchInput]} value={name} onChangeText={value => { setName(value); setResults([]); setSearched(false); }} maxLength={120} returnKeyType="search" onSubmitEditing={() => void search()} placeholder="예: 경복궁" /><Pressable disabled={searching || busy} onPress={() => void search()} style={styles.searchButton}>{searching ? <ActivityIndicator color="#FFF" /> : <Text style={styles.searchButtonText}>검색</Text>}</Pressable></View>
      {results.length ? <View style={styles.results}>{results.map(result => <Pressable key={result.providerId} onPress={() => selectResult(result)} style={styles.result}><Text style={styles.resultName}>{result.name}</Text><Text style={styles.resultAddress}>{result.address || '주소 정보 없음'}</Text><Text style={styles.resultCoords}>{result.latitude}, {result.longitude}</Text></Pressable>)}</View> : searched ? <Text style={styles.noResult}>검색 결과가 없습니다.</Text> : null}
      <Text style={styles.label}>주소</Text><TextInput style={styles.input} value={address} onChangeText={setAddress} maxLength={300} placeholder="예: 서울 종로구 사직로 161" />
      <View style={styles.row}><View style={styles.half}><Text style={styles.label}>위도 (선택)</Text><TextInput style={styles.input} value={latitude} onChangeText={setLatitude} keyboardType="numbers-and-punctuation" placeholder="37.579617" /></View><View style={styles.half}><Text style={styles.label}>경도 (선택)</Text><TextInput style={styles.input} value={longitude} onChangeText={setLongitude} keyboardType="numbers-and-punctuation" placeholder="126.977041" /></View></View>
      {error ? <Text style={styles.error}>{error}</Text> : null}</ScrollView>
  </SafeAreaView></Modal>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { height: 58, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', backgroundColor: '#FFF' }, back: { color: '#1677D2', fontSize: 16, fontWeight: '700' }, headerTitle: { flex: 1, textAlign: 'center', color: '#17212B', fontSize: 18, fontWeight: '800' }, add: { color: '#1677D2', fontWeight: '800' }, list: { padding: 18 }, grow: { flexGrow: 1 }, card: { backgroundColor: '#FFF', borderRadius: 14, padding: 17, marginBottom: 12, borderWidth: 1, borderColor: '#E7EBEF', flexDirection: 'row', alignItems: 'center' }, cardText: { flex: 1 }, name: { color: '#17212B', fontSize: 17, fontWeight: '800' }, address: { color: '#65717E', marginTop: 5 }, coords: { color: '#8B96A1', fontSize: 12, marginTop: 4 }, delete: { color: '#D92D20', fontWeight: '700' }, form: { padding: 22, paddingBottom: 60, gap: 10 }, label: { color: '#33404D', fontWeight: '700', marginTop: 8 }, input: { height: 50, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, backgroundColor: '#FFF', paddingHorizontal: 14, fontSize: 15 }, searchRow: { flexDirection: 'row', gap: 9 }, searchInput: { flex: 1 }, searchButton: { width: 70, height: 50, borderRadius: 12, backgroundColor: '#208AEF', alignItems: 'center', justifyContent: 'center' }, searchButtonText: { color: '#FFF', fontWeight: '800' }, results: { borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, overflow: 'hidden', backgroundColor: '#FFF' }, result: { padding: 13, borderBottomWidth: 1, borderBottomColor: '#EEF1F4' }, resultName: { color: '#17212B', fontWeight: '800' }, resultAddress: { color: '#65717E', fontSize: 13, marginTop: 4 }, resultCoords: { color: '#8B96A1', fontSize: 11, marginTop: 3 }, noResult: { color: '#65717E', backgroundColor: '#FFF', padding: 14, borderRadius: 10 }, row: { flexDirection: 'row', gap: 10 }, half: { flex: 1, gap: 8 }, error: { color: '#B42318', backgroundColor: '#FFF0F0', padding: 12, borderRadius: 10, marginTop: 8 },
});
