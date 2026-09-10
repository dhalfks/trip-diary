import { useCallback, useEffect, useState } from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { ScreenState } from '@/features/trips/screen-state';
import { TripFormScreen } from '@/features/trips/trip-form-screen';
import { tripApi } from '@/features/trips/trip-api';
import type { Trip, TripInput } from '@/features/trips/types';
import { errorMessage } from '@/lib/api';

export default function EditTripScreen() {
  const { tripId } = useLocalSearchParams<{ tripId: string }>();
  const [trip, setTrip] = useState<Trip>(); const [error, setError] = useState<string>();
  const load = useCallback(async () => {
    if (!tripId) return setError('여행 식별자가 올바르지 않습니다.');
    try { setTrip(await tripApi.get(tripId)); setError(undefined); } catch (reason) { setError(errorMessage(reason)); }
  }, [tripId]);
  useEffect(() => {
    // The loader updates state only after its awaited API request settles.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);
  const safe = { flex: 1, backgroundColor: '#F7FAFC' } as const;
  if (error) return <SafeAreaView style={safe}><ScreenState title="여행을 불러오지 못했어요" description={error} actionLabel="다시 시도" onAction={() => void load()} /></SafeAreaView>;
  if (!trip) return <SafeAreaView style={safe}><ScreenState loading title="여행을 불러오는 중이에요" /></SafeAreaView>;
  return <TripFormScreen mode="edit" initialTrip={trip} onSubmit={(input: TripInput) => tripApi.update(trip.id, input)} />;
}
