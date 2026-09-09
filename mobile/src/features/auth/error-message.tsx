import { StyleSheet, Text, View } from 'react-native';
export function ErrorMessage({ message }: { message?: string }) {
  return message ? <View accessibilityRole="alert" style={styles.box}><Text style={styles.text}>{message}</Text></View> : null;
}
const styles = StyleSheet.create({ box: { backgroundColor: '#FFF0F0', borderRadius: 10, padding: 12 }, text: { color: '#B42318', lineHeight: 20 } });
