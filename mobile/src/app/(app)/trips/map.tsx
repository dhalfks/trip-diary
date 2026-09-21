import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ScreenState } from '@/features/trips/screen-state';
import { isValidCoordinate, TripPlacesMap } from '@/features/trips/place-map';
import { placeApi, tripApi } from '@/features/trips/trip-api';
import type { Place, Trip } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';

async function loadAllTrips() {
  const trips: Trip[] = [];
  let page = 0;
  let last = false;
  while (!last) {
    const result = await tripApi.list(page);
    trips.push(...result.content);
    last = result.last;
    page += 1;
  }
  return trips;
}

export default function AllTripsMapScreen() {
  const [places, setPlaces] = useState<Place[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const mappedPlaces = useMemo(() => places.filter(isValidCoordinate), [places]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const trips = await loadAllTrips();
      const placeLists = await Promise.all(trips.map(trip => placeApi.list(trip.id)));
      setPlaces(placeLists.flat());
      setError(undefined);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // The loader updates state after its API requests settle.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  return <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
    <View style={styles.header}>
      <Pressable onPress={() => router.back()} hitSlop={12}><Text style={styles.back}>‹ 홈</Text></Pressable>
      <Text style={styles.title}>여행 지도</Text>
      <View style={styles.spacer} />
    </View>
    {loading ? <ScreenState loading title="장소를 불러오는 중이에요" /> : error ?
      <ScreenState title="장소를 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => void load()} /> :
      mappedPlaces.length === 0 ? <ScreenState title="지도에 표시할 장소가 없어요" description="위도와 경도가 저장된 장소를 추가해 주세요." /> :
      <TripPlacesMap places={mappedPlaces} />}
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' },
  header: { height: 58, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', backgroundColor: '#FFF' },
  back: { color: '#1677D2', fontSize: 16, fontWeight: '700' },
  title: { flex: 1, color: '#17212B', fontSize: 18, fontWeight: '800', textAlign: 'center' },
  spacer: { width: 44 },
});
