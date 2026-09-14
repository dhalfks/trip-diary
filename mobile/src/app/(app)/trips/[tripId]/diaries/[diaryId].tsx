import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { ActivityIndicator, AppState, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { bookApi } from '@/features/books/book-api';
import { previewPage } from '@/features/books/book-model';
import { createPreviewStore } from '@/features/books/book-store';
import { DiaryPageView } from '@/features/books/diary-page-view';
import { imageRefreshDelay } from '@/features/images/image-gallery-store';

export default function DiaryPreviewScreen() {
  const { tripId, diaryId } = useLocalSearchParams<{ tripId: string; diaryId: string }>();
  const store = useMemo(() => createPreviewStore(() => bookApi.get(tripId, diaryId)), [tripId, diaryId]);
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  useEffect(() => {
    void store.load();
    const subscription = AppState.addEventListener('change', next => { if (next === 'active') void store.load(); });
    return () => { subscription.remove(); store.cancel(); };
  }, [store]);
  useEffect(() => {
    if (!state.detail || state.error || state.loading) return;
    const delay = imageRefreshDelay(state.detail.images);
    if (delay === undefined) return;
    const timer = setTimeout(() => { if (Platform.OS === 'web' || AppState.currentState === 'active') void store.load(); }, delay);
    return () => clearTimeout(timer);
  }, [state.detail, state.error, state.loading, store]);

  const page = previewPage(state.detail, state.index);
  const count = state.detail?.pages.length ?? 0;
  return <SafeAreaView style={styles.safe}>
    <View style={styles.header}><Pressable onPress={() => router.back()}><Text style={styles.link}>‹ 다이어리</Text></Pressable><Text style={styles.heading}>미리보기</Text><Pressable disabled={state.loading} onPress={() => void store.load()}><Text style={styles.link}>새로고침</Text></Pressable></View>
    {state.detail ? <Text numberOfLines={1} style={styles.bookTitle}>{state.detail.diary.title}</Text> : null}
    {state.loading ? <ActivityIndicator color="#208AEF" style={styles.loading} /> : null}
    {state.error ? <Pressable onPress={() => void store.load()}><Text accessibilityRole="alert" style={styles.error}>{state.error} · 다시 시도</Text></Pressable> : null}
    <ScrollView key={page?.id ?? 'empty'} contentContainerStyle={styles.content}>
      {page && state.detail ? <DiaryPageView page={page} template={state.detail.diary.templateType} revision={state.revision} onRefresh={() => void store.load()} />
        : !state.loading ? <Text style={styles.empty}>표시할 페이지가 없어요.</Text> : null}
    </ScrollView>
    <View style={styles.controls}><Pressable accessibilityRole="button" disabled={!count || state.index === 0} onPress={() => store.move(-1)}><Text style={[styles.link, (!count || state.index === 0) && styles.disabled]}>‹ 이전</Text></Pressable>
      <Text accessibilityLiveRegion="polite" style={styles.pageNumber}>{count ? state.index + 1 : 0} / {count}</Text>
      <Pressable accessibilityRole="button" disabled={!count || state.index >= count - 1} onPress={() => store.move(1)}><Text style={[styles.link, (!count || state.index >= count - 1) && styles.disabled]}>다음 ›</Text></Pressable></View>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#EDE9E2' }, header: { padding: 16, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#FFF' },
  link: { color: '#1677D2', fontWeight: '700', padding: 5 }, heading: { color: '#17212B', fontSize: 17, fontWeight: '800' },
  bookTitle: { color: '#526273', textAlign: 'center', paddingTop: 12, paddingHorizontal: 18, fontSize: 13 }, content: { padding: 18, paddingBottom: 30 },
  controls: { backgroundColor: '#FFF', paddingHorizontal: 25, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pageNumber: { color: '#526273', fontWeight: '700' }, disabled: { opacity: 0.3 }, loading: { padding: 10 }, empty: { textAlign: 'center', color: '#65717E', marginTop: 50 },
  error: { margin: 14, padding: 10, borderRadius: 8, backgroundColor: '#FFF0F0', color: '#B42318' },
});
