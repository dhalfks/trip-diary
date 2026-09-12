import { Image } from 'expo-image';
import { useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { ActivityIndicator, Alert, AppState, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { imageApi } from './image-api';
import { createImageGallery, imageRefreshDelay } from './image-gallery-store';
import type { DiaryImage, ImageTarget } from './types';

export function ImageGallery({ target: { tripId, dayId, entryId }, refreshKey }: { target: ImageTarget; refreshKey: string }) {
  const gallery = useMemo(() => {
    const target = { tripId, dayId, entryId };
    return createImageGallery({ list: () => imageApi.list(target), remove: imageId => imageApi.remove(target, imageId) });
  }, [tripId, dayId, entryId]);
  const state = useSyncExternalStore(gallery.subscribe, gallery.getSnapshot, gallery.getSnapshot);

  useEffect(() => { void gallery.load(); }, [gallery, refreshKey]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', next => { if (next === 'active') void gallery.load(); });
    return () => { subscription.remove(); gallery.cancel(); };
  }, [gallery]);
  useEffect(() => {
    if (state.error || state.loading) return;
    const delay = imageRefreshDelay(state.images);
    if (delay === undefined) return;
    const timer = setTimeout(() => {
      if (Platform.OS === 'web' || AppState.currentState === 'active') void gallery.load();
    }, delay);
    return () => clearTimeout(timer);
  }, [gallery, state.images, state.error, state.loading]);

  function confirmDelete(image: DiaryImage) {
    if (state.deletingId) return;
    const remove = () => { void gallery.remove(image.id); };
    if (Platform.OS === 'web') { if (globalThis.confirm?.('이 사진을 삭제할까요?')) remove(); }
    else Alert.alert('사진 삭제', '이 사진을 기록에서 삭제할까요?', [{ text: '취소', style: 'cancel' }, { text: '삭제', style: 'destructive', onPress: remove }]);
  }

  return <View style={styles.gallery}>
    <View style={styles.heading}><Text style={styles.label}>사진 {state.images.length}장</Text>{state.loading ? <ActivityIndicator size="small" color="#208AEF" /> : null}</View>
    {!state.loading && !state.error && !state.images.length ? <Text style={styles.help}>등록된 사진이 없어요.</Text> : null}
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.photos}>
      {state.images.map(image => <View key={image.id} style={styles.card}>
        <PrivatePhoto key={`${image.downloadUrl}:${state.revision}`} image={image} onRetry={() => void gallery.load()} />
        <Pressable accessibilityRole="button" accessibilityLabel={`${image.originalFileName} 삭제`} disabled={!!state.deletingId}
          onPress={event => { event.stopPropagation(); confirmDelete(image); }} style={styles.deleteButton}>
          <Text style={[styles.delete, !!state.deletingId && styles.disabled]}>{state.deletingId === image.id ? '삭제 중…' : '사진 삭제'}</Text>
        </Pressable>
      </View>)}
    </ScrollView>
    {state.notice ? <Text accessibilityLiveRegion="polite" style={styles.success}>{state.notice}</Text> : null}
    {state.error ? <View><Text accessibilityRole="alert" style={styles.error}>{state.error}</Text><Pressable accessibilityRole="button"
      onPress={event => { event.stopPropagation(); void gallery.load(); }}><Text style={styles.link}>사진 목록 다시 불러오기</Text></Pressable></View> : null}
  </View>;
}

function PrivatePhoto({ image, onRetry }: { image: DiaryImage; onRetry(): void }) {
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  return <View style={styles.frame}>
    <Image source={{ uri: image.downloadUrl }} cachePolicy="none" accessibilityLabel={image.originalFileName}
      contentFit="cover" style={styles.photo} onLoad={() => setLoading(false)} onError={() => { setFailed(true); setLoading(false); }} />
    {loading ? <View style={styles.overlay}><ActivityIndicator color="#208AEF" /></View> : null}
    {failed ? <Pressable accessibilityRole="button" style={styles.overlay} onPress={event => { event.stopPropagation(); onRetry(); }}>
      <Text style={styles.help}>사진을 불러오지 못했어요</Text><Text style={styles.link}>다시 시도</Text>
    </Pressable> : null}
  </View>;
}

const styles = StyleSheet.create({
  gallery: { marginTop: 14, gap: 8 }, heading: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  label: { color: '#33404D', fontWeight: '700', fontSize: 13 }, help: { color: '#65717E', fontSize: 12, textAlign: 'center' },
  photos: { gap: 10 }, card: { width: 148 }, frame: { width: 148, height: 120, borderRadius: 10, overflow: 'hidden', backgroundColor: '#EDF2F7' },
  photo: { width: '100%', height: '100%' }, overlay: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center', padding: 8, backgroundColor: '#EDF2F7' },
  deleteButton: { alignItems: 'center', paddingVertical: 8 }, delete: { color: '#B42318', fontSize: 12, fontWeight: '700' },
  disabled: { opacity: 0.45 }, success: { color: '#15803D', fontSize: 12 }, error: { color: '#B42318', fontSize: 12 },
  link: { color: '#1677D2', fontWeight: '700', fontSize: 12, paddingVertical: 6 },
});
