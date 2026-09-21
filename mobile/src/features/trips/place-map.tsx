import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import * as Location from 'expo-location';
import MapView, { Marker, PROVIDER_GOOGLE, type Region } from 'react-native-maps';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { Place } from './types';

export type Coordinate = { latitude: number; longitude: number };

const WORLD_REGION: Region = {
  latitude: 0,
  longitude: 0,
  latitudeDelta: 80,
  longitudeDelta: 120,
};

export function isValidCoordinate(place: Place): place is Place & Coordinate {
  return typeof place.latitude === 'number' && Number.isFinite(place.latitude) && place.latitude >= -90 && place.latitude <= 90
    && typeof place.longitude === 'number' && Number.isFinite(place.longitude) && place.longitude >= -180 && place.longitude <= 180;
}

export function TripPlacesMap({ places }: { places: (Place & Coordinate)[] }) {
  const mapRef = useRef<MapView>(null);
  const first = places[0];

  function fitMarkers() {
    if (places.length > 1) {
      mapRef.current?.fitToCoordinates(places, {
        edgePadding: { top: 70, right: 50, bottom: 70, left: 50 },
        animated: false,
      });
    }
  }

  return <MapView
    ref={mapRef}
    provider={PROVIDER_GOOGLE}
    style={styles.map}
    initialRegion={{
      latitude: first.latitude,
      longitude: first.longitude,
      latitudeDelta: places.length === 1 ? 0.05 : 10,
      longitudeDelta: places.length === 1 ? 0.05 : 10,
    }}
    onMapReady={fitMarkers}
  >
    {places.map(place => <Marker
      key={place.id}
      coordinate={{ latitude: place.latitude, longitude: place.longitude }}
      title={place.name}
      description={place.address ?? undefined}
    />)}
  </MapView>;
}

export function LocationPicker({
  visible,
  initialCoordinate,
  onCancel,
  onSelect,
}: {
  visible: boolean;
  initialCoordinate?: Coordinate;
  onCancel(): void;
  onSelect(coordinate: Coordinate): void;
}) {
  const mapRef = useRef<MapView>(null);
  const [region, setRegion] = useState<Region>(() => initialCoordinate ? { ...initialCoordinate, latitudeDelta: 0.02, longitudeDelta: 0.02 } : WORLD_REGION);
  const [locating, setLocating] = useState(!initialCoordinate);
  const [locationMessage, setLocationMessage] = useState<string>();

  useEffect(() => {
    if (!visible || initialCoordinate) return;
    let active = true;

    async function locate() {
      try {
        const permission = await Location.requestForegroundPermissionsAsync();
        if (permission.status !== Location.PermissionStatus.GRANTED) {
          if (active) setLocationMessage('위치 권한 없이도 지도를 움직여 위치를 선택할 수 있어요.');
          return;
        }
        const lastKnown = await Location.getLastKnownPositionAsync();
        const location = lastKnown ?? await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
        if (!active) return;
        const next = { latitude: location.coords.latitude, longitude: location.coords.longitude, latitudeDelta: 0.02, longitudeDelta: 0.02 };
        setRegion(next);
        mapRef.current?.animateToRegion(next, 400);
      } catch {
        if (active) setLocationMessage('현재 위치를 확인하지 못했어요. 지도를 움직여 직접 선택해 주세요.');
      } finally {
        if (active) setLocating(false);
      }
    }

    void locate();
    return () => { active = false; };
  }, [initialCoordinate, visible]);

  return <Modal visible={visible} animationType="slide" onRequestClose={onCancel}>
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Pressable onPress={onCancel} hitSlop={12}><Text style={styles.link}>취소</Text></Pressable>
        <Text style={styles.title}>지도에서 위치 선택</Text>
        <View style={styles.headerSpacer} />
      </View>
      <View style={styles.mapArea}>
        <MapView
          ref={mapRef}
          provider={PROVIDER_GOOGLE}
          style={styles.map}
          initialRegion={region}
          showsUserLocation
          onRegionChangeComplete={setRegion}
          onPress={event => {
            const next = { ...event.nativeEvent.coordinate, latitudeDelta: region.latitudeDelta, longitudeDelta: region.longitudeDelta };
            setRegion(next);
            mapRef.current?.animateToRegion(next, 250);
          }}
        />
        <View pointerEvents="none" style={styles.pin}><Text style={styles.pinText}>●</Text></View>
        {locating ? <View style={styles.locationStatus}><ActivityIndicator color="#1677D2" /><Text style={styles.statusText}>현재 위치 확인 중</Text></View> : null}
        {locationMessage ? <View style={styles.message}><Text style={styles.messageText}>{locationMessage}</Text></View> : null}
      </View>
      <View style={styles.footer}>
        <Text style={styles.coordinate}>{region.latitude.toFixed(6)}, {region.longitude.toFixed(6)}</Text>
        <Pressable style={styles.selectButton} onPress={() => onSelect({ latitude: region.latitude, longitude: region.longitude })}>
          <Text style={styles.selectText}>이 위치를 선택</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  </Modal>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' },
  header: { height: 58, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', backgroundColor: '#FFF' },
  link: { color: '#1677D2', fontSize: 16, fontWeight: '700' },
  title: { flex: 1, color: '#17212B', fontSize: 18, fontWeight: '800', textAlign: 'center' },
  headerSpacer: { width: 36 },
  mapArea: { flex: 1 },
  map: { flex: 1 },
  pin: { position: 'absolute', left: '50%', top: '50%', marginLeft: -12, marginTop: -28, width: 24, height: 28, alignItems: 'center', justifyContent: 'center' },
  pinText: { color: '#D92D20', fontSize: 28 },
  locationStatus: { position: 'absolute', top: 14, alignSelf: 'center', flexDirection: 'row', gap: 8, paddingHorizontal: 13, paddingVertical: 9, borderRadius: 18, backgroundColor: '#FFF' },
  statusText: { color: '#33404D', fontWeight: '700' },
  message: { position: 'absolute', left: 16, right: 16, bottom: 16, padding: 12, borderRadius: 10, backgroundColor: 'rgba(255,255,255,0.95)' },
  messageText: { color: '#526273', textAlign: 'center' },
  footer: { padding: 16, gap: 10, backgroundColor: '#FFF', borderTopWidth: 1, borderTopColor: '#E7EBEF' },
  coordinate: { color: '#65717E', textAlign: 'center', fontSize: 13 },
  selectButton: { height: 52, alignItems: 'center', justifyContent: 'center', borderRadius: 13, backgroundColor: '#208AEF' },
  selectText: { color: '#FFF', fontSize: 16, fontWeight: '800' },
});
