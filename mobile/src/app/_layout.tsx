import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { AuthProvider, useAuth } from '@/features/auth/auth-context';

SplashScreen.preventAutoHideAsync();

function Navigator() {
  const { initialized, isAuthenticated } = useAuth();
  useEffect(() => { if (initialized) void SplashScreen.hideAsync(); }, [initialized]);
  if (!initialized) return null;
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Protected guard={!isAuthenticated}><Stack.Screen name="(auth)" /></Stack.Protected>
      <Stack.Protected guard={isAuthenticated}><Stack.Screen name="(app)" /></Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  const scheme = useColorScheme();
  return <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}><AuthProvider><Navigator /></AuthProvider></ThemeProvider>;
}
