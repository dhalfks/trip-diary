import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import type { Place } from './types';

export type Coordinate = { latitude: number; longitude: number };

export function isValidCoordinate(place: Place): place is Place & Coordinate {
  return typeof place.latitude === 'number' && Number.isFinite(place.latitude) && place.latitude >= -90 && place.latitude <= 90
    && typeof place.longitude === 'number' && Number.isFinite(place.longitude) && place.longitude >= -180 && place.longitude <= 180;
}

export function TripPlacesMap({ places }: { places: (Place & Coordinate)[] }) {
  return <View style={styles.center}><Text style={styles.title}>여행 지도는 Android 앱에서 확인해 주세요.</Text><Text style={styles.text}>{places.length}개 장소의 좌표가 저장되어 있습니다.</Text></View>;
}

export function LocationPicker({ visible, onCancel }: { visible: boolean; initialCoordinate?: Coordinate; onCancel(): void; onSelect(coordinate: Coordinate): void }) {
  return <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
    <View style={styles.overlay}><View style={styles.dialog}><Text style={styles.title}>지도 위치 선택은 Android 앱에서 사용할 수 있어요.</Text><Pressable style={styles.button} onPress={onCancel}><Text style={styles.buttonText}>확인</Text></Pressable></View></View>
  </Modal>;
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#F7FAFC' },
  title: { color: '#17212B', fontSize: 17, fontWeight: '800', textAlign: 'center' },
  text: { color: '#65717E', marginTop: 8 },
  overlay: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: 'rgba(0,0,0,0.35)' },
  dialog: { width: '100%', maxWidth: 420, padding: 24, borderRadius: 16, backgroundColor: '#FFF' },
  button: { marginTop: 20, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: '#208AEF' },
  buttonText: { color: '#FFF', fontWeight: '800' },
});
