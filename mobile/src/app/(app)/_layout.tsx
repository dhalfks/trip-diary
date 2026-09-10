import { Stack } from 'expo-router';

export default function AppLayout() {
  return <Stack screenOptions={{ headerShown: false }}>
    <Stack.Screen name="home" />
    <Stack.Screen name="trips/new" options={{ presentation: 'modal' }} />
    <Stack.Screen name="trips/[tripId]" />
    <Stack.Screen name="trips/[tripId]/edit" options={{ presentation: 'modal' }} />
  </Stack>;
}
