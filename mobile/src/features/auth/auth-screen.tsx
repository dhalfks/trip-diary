import type { PropsWithChildren } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

export function AuthScreen({ title, subtitle, children }: PropsWithChildren<{ title: string; subtitle: string }>) {
  return <SafeAreaView style={styles.safe}><KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
    <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled"><View style={styles.card}>
      <Text style={styles.brand}>TRIP DIARY</Text><Text style={styles.title}>{title}</Text><Text style={styles.subtitle}>{subtitle}</Text>
      <View style={styles.form}>{children}</View>
    </View></ScrollView>
  </KeyboardAvoidingView></SafeAreaView>;
}
const styles = StyleSheet.create({
  flex: { flex: 1 }, safe: { flex: 1, backgroundColor: '#F7FAFC' }, scroll: { flexGrow: 1, justifyContent: 'center', padding: 24 },
  card: { width: '100%', maxWidth: 440, alignSelf: 'center' }, brand: { color: '#208AEF', fontWeight: '800', letterSpacing: 2 },
  title: { color: '#17212B', fontSize: 32, lineHeight: 40, fontWeight: '800', marginTop: 16 },
  subtitle: { color: '#65717E', fontSize: 16, marginTop: 8 }, form: { gap: 14, marginTop: 32 },
});
