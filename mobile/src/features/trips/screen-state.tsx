import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

type Props = { title: string; description?: string; actionLabel?: string; onAction?: () => void; loading?: boolean };

export function ScreenState({ title, description, actionLabel, onAction, loading }: Props) {
  return <View style={styles.container}>
    {loading ? <ActivityIndicator size="large" color="#208AEF" /> : null}
    <Text style={styles.title}>{title}</Text>
    {description ? <Text style={styles.description}>{description}</Text> : null}
    {actionLabel && onAction ? <Pressable style={styles.button} onPress={onAction}><Text style={styles.buttonText}>{actionLabel}</Text></Pressable> : null}
  </View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  title: { color: '#17212B', fontSize: 20, fontWeight: '800', marginTop: 16, textAlign: 'center' },
  description: { color: '#65717E', fontSize: 15, lineHeight: 22, marginTop: 8, textAlign: 'center' },
  button: { backgroundColor: '#208AEF', borderRadius: 12, marginTop: 20, paddingHorizontal: 22, paddingVertical: 13 },
  buttonText: { color: '#FFF', fontSize: 15, fontWeight: '800' },
});
