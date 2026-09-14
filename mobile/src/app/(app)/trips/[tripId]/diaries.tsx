import { Image } from 'expo-image';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { ActivityIndicator, Alert, FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { bookApi } from '@/features/books/book-api';
import { collectCoverImages } from '@/features/books/book-model';
import { createBookStore } from '@/features/books/book-store';
import type { TemplateType, TravelDiary } from '@/features/books/types';
import { imageApi } from '@/features/images/image-api';
import type { DiaryImage } from '@/features/images/types';
import { diaryApi, tripApi } from '@/features/trips/trip-api';
import { errorMessage } from '@/lib/api';

export default function TravelDiariesScreen() {
  const { tripId } = useLocalSearchParams<{ tripId: string }>();
  const store = useMemo(() => createBookStore({ list: () => bookApi.list(tripId), create: input => bookApi.create(tripId, input), remove: id => bookApi.remove(tripId, id) }), [tripId]);
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot);
  const [title, setTitle] = useState('');
  const [template, setTemplate] = useState<TemplateType>('CLASSIC');
  const [coverId, setCoverId] = useState<string | null>(null);
  const [covers, setCovers] = useState<DiaryImage[]>([]);
  const [coversLoading, setCoversLoading] = useState(false);
  const [coversLoaded, setCoversLoaded] = useState(false);
  const [coverError, setCoverError] = useState<string>();
  const mounted = useRef(true);
  const picking = useRef(false);

  useEffect(() => {
    mounted.current = true; void store.load();
    return () => { mounted.current = false; store.cancel(); };
  }, [store]);

  async function chooseCover() {
    if (picking.current) return;
    picking.current = true; setCoversLoading(true); setCoverError(undefined);
    try {
      const trip = await tripApi.get(tripId);
      const images = await collectCoverImages(trip.days, {
        entries: dayId => diaryApi.list(tripId, dayId), images: (dayId, entryId) => imageApi.list({ tripId, dayId, entryId }),
      });
      if (mounted.current) { setCovers(images); setCoversLoaded(true); }
    } catch (error) { if (mounted.current) setCoverError(errorMessage(error)); }
    finally { picking.current = false; if (mounted.current) setCoversLoading(false); }
  }

  async function generate() {
    const diary = await store.create({ title: title.trim() || null, templateType: template, coverImageId: coverId });
    if (diary) router.push({ pathname: '/trips/[tripId]/diaries/[diaryId]', params: { tripId, diaryId: diary.id } });
  }

  function remove(diary: TravelDiary) {
    if (state.busy) return;
    const action = () => { void store.remove(diary.id); };
    const text = '생성된 다이어리를 삭제할까요? 원본 기록과 사진은 유지됩니다.';
    if (Platform.OS === 'web') { if (globalThis.confirm?.(text)) action(); }
    else Alert.alert('다이어리 삭제', text, [{ text: '취소', style: 'cancel' }, { text: '삭제', style: 'destructive', onPress: action }]);
  }

  return <SafeAreaView style={styles.safe}>
    <View style={styles.header}><Pressable onPress={() => router.back()}><Text style={styles.link}>‹ 여행</Text></Pressable><Text style={styles.heading}>여행 다이어리</Text><View style={styles.spacer} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.title}>여행의 기억을 한 권으로</Text><Text style={styles.help}>기록과 사진을 모아 다이어리를 만듭니다.</Text>
      <Text style={styles.label}>제목 (선택)</Text><TextInput editable={!state.busy} value={title} onChangeText={setTitle} maxLength={120} placeholder="비워두면 여행 이름을 사용해요" style={styles.input} />
      <Text style={styles.label}>템플릿</Text>
      <View style={styles.templates}>{(['CLASSIC', 'PHOTO'] as const).map(type => <Pressable key={type} accessibilityRole="radio" accessibilityState={{ checked: template === type }} disabled={state.busy}
        onPress={() => setTemplate(type)} style={[styles.template, template === type && styles.selected]}>
        <Text style={styles.templateName}>{type === 'CLASSIC' ? '클래식' : '포토'}</Text><Text style={styles.help}>{type === 'CLASSIC' ? '글과 사진을 차분하게\n사진을 2장씩 배치' : '사진을 크게\n사진과 글을 함께 배치'}</Text>
      </Pressable>)}</View>
      <Text style={styles.label}>대표 사진</Text>
      <View style={styles.row}><Pressable disabled={state.busy} onPress={() => setCoverId(null)}><Text style={styles.link}>{coverId ? '자동 선택으로 변경' : '자동 선택 ✓'}</Text></Pressable>
        <Pressable disabled={state.busy || coversLoading} onPress={() => void chooseCover()}><Text style={styles.link}>{coversLoading ? '사진 불러오는 중…' : '직접 선택'}</Text></Pressable></View>
      {coversLoaded && !covers.length ? <Text style={styles.help}>업로드 완료된 사진이 없어요. 사진 없이도 만들 수 있어요.</Text> : null}
      {coverError ? <Text style={styles.error}>{coverError}</Text> : null}
      {covers.length ? <FlatList horizontal data={covers} keyExtractor={image => image.id} extraData={coverId} contentContainerStyle={styles.coverList}
        renderItem={({ item }) => <Pressable accessibilityRole="radio" accessibilityLabel={item.originalFileName} accessibilityState={{ checked: coverId === item.id }} disabled={state.busy}
          onPress={() => setCoverId(item.id)} style={[styles.coverChoice, coverId === item.id && styles.selected]}>
          <Image source={{ uri: item.downloadUrl }} cachePolicy="none" style={styles.coverThumb} contentFit="cover" /><Text numberOfLines={1} style={styles.help}>{coverId === item.id ? '대표 사진 ✓' : item.originalFileName}</Text>
        </Pressable>} /> : null}
      <Pressable accessibilityRole="button" disabled={state.loading || state.busy || state.diaries.length >= 5} onPress={() => void generate()}
        style={[styles.generate, (state.loading || state.busy || state.diaries.length >= 5) && styles.disabled]}>
        {state.busy && !state.deletingId ? <ActivityIndicator color="#FFF" /> : null}<Text style={styles.generateText}>{state.busy && !state.deletingId ? '다이어리 만드는 중…' : '여행 다이어리 만들기'}</Text>
      </Pressable>
      <Text style={styles.help}>다시 만들면 새 다이어리로 저장됩니다. 여행당 최대 5개까지 보관할 수 있어요.</Text>
      {state.notice ? <Text accessibilityLiveRegion="polite" style={styles.success}>{state.notice}</Text> : null}
      {state.error ? <Text accessibilityRole="alert" style={styles.error}>{state.error}</Text> : null}
      <View style={styles.row}><Text style={styles.label}>내 다이어리 {state.diaries.length}/5</Text><Pressable disabled={state.busy || state.loading} onPress={() => void store.load()}><Text style={styles.link}>새로고침</Text></Pressable></View>
      {state.loading ? <ActivityIndicator color="#208AEF" /> : null}
      {!state.loading && !state.diaries.length ? <Text style={styles.help}>아직 만든 다이어리가 없어요.</Text> : null}
      {state.diaries.map(diary => <View key={diary.id} style={styles.result}>
        <Pressable disabled={state.busy} onPress={() => router.push({ pathname: '/trips/[tripId]/diaries/[diaryId]', params: { tripId, diaryId: diary.id } })}>
          <Text style={styles.resultTitle}>{diary.title}</Text><Text style={styles.help}>{diary.templateType} · {diary.pageCount}페이지 · {new Date(diary.createdAt).toLocaleString()}</Text><Text style={styles.link}>미리보기 ›</Text>
        </Pressable>
        <Pressable disabled={state.busy} onPress={() => remove(diary)}><Text style={styles.delete}>{state.deletingId === diary.id ? '삭제 중…' : '다이어리 삭제'}</Text></Pressable>
      </View>)}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, header: { backgroundColor: '#FFF', padding: 18, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  spacer: { width: 35 }, heading: { fontSize: 18, fontWeight: '800', color: '#17212B' }, content: { padding: 22, gap: 12, paddingBottom: 55 },
  title: { fontSize: 25, fontWeight: '800', color: '#17212B' }, help: { color: '#65717E', fontSize: 13, lineHeight: 21 }, label: { color: '#33404D', fontWeight: '700', marginTop: 8 },
  input: { borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 10, padding: 13, backgroundColor: '#FFF', minHeight: 48 }, templates: { flexDirection: 'row', gap: 12 },
  template: { flex: 1, borderRadius: 12, borderWidth: 2, borderColor: '#E7EBEF', padding: 15, gap: 8, backgroundColor: '#FFF' }, selected: { borderColor: '#208AEF', backgroundColor: '#EAF4FE' },
  templateName: { fontSize: 17, fontWeight: '800', color: '#263342' }, row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 14 },
  link: { color: '#1677D2', fontWeight: '700', paddingVertical: 6 }, coverList: { gap: 8 }, coverChoice: { width: 96, padding: 5, borderWidth: 2, borderColor: '#E7EBEF', borderRadius: 8 }, coverThumb: { width: 82, height: 72, borderRadius: 4 },
  generate: { backgroundColor: '#208AEF', borderRadius: 11, padding: 16, flexDirection: 'row', justifyContent: 'center', gap: 10 }, generateText: { color: '#FFF', fontWeight: '800' },
  disabled: { opacity: 0.45 }, success: { color: '#15803D' }, error: { backgroundColor: '#FFF0F0', color: '#B42318', padding: 10, borderRadius: 8 },
  result: { backgroundColor: '#FFF', borderRadius: 12, borderWidth: 1, borderColor: '#E7EBEF', padding: 16, gap: 8 }, resultTitle: { color: '#17212B', fontSize: 17, fontWeight: '700' }, delete: { color: '#B42318', fontSize: 13, paddingVertical: 6 },
});
