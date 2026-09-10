import { TripFormScreen } from '@/features/trips/trip-form-screen';
import { tripApi } from '@/features/trips/trip-api';

export default function NewTripScreen() {
  return <TripFormScreen mode="create" onSubmit={tripApi.create} />;
}
