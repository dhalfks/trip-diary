import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { errorMessage } from '@/lib/api';
import { imageApi } from './image-api';
import { preparePhoto } from './photo-file';
import { putImage } from './s3-upload';
import type { ImageTarget, UploadItem, UploadPhase } from './types';
import { isRateLimited, uploadImage } from './upload-flow';

const labels: Record<UploadPhase, string> = {
  queued: '업로드 대기', signing: '업로드 준비 중', uploading: '사진 전송 중',
  completing: '기록에 사진을 저장하는 중', success: '업로드 완료', error: '업로드 실패',
};

export function ImageUploadModal({ target, title, onClose, onUploaded }: { target: ImageTarget; title: string; onClose(): void; onUploaded?(): void }) {
  const [items, setItems] = useState<UploadItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string>();
  const queue = useRef<UploadItem[]>([]);
  const busyRef = useRef(false);
  const pickingRef = useRef(false);
  const mounted = useRef(true);
  const sequence = useRef(0);
  const controller = useRef<AbortController | null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; controller.current?.abort(); };
  }, []);

  function commit(next: UploadItem[]) {
    queue.current = next;
    if (mounted.current) setItems(next);
  }

  function patch(id: string, update: Partial<UploadItem>) {
    commit(queue.current.map(item => item.id === id ? { ...item, ...update } : item));
  }

  async function run(ids: string[]) {
    if (busyRef.current || !mounted.current) return;
    busyRef.current = true; setBusy(true);
    const abort = new AbortController();
    controller.current = abort;
    try {
      for (const id of ids) {
        if (abort.signal.aborted) break;
        const item = queue.current.find(candidate => candidate.id === id);
        if (!item || item.phase === 'success') continue;
        patch(id, { error: undefined, rateLimited: false });
        try {
          await uploadImage(item.file, item.checkpoint, {
            initiate: file => imageApi.initiate(target, file),
            complete: imageId => imageApi.complete(target, imageId),
            put: putImage,
          }, (phase, progress) => { if (mounted.current) patch(id, { phase, progress: progress ?? 0 }); }, abort.signal);
          if (mounted.current) onUploaded?.();
        } catch (reason) {
          if (mounted.current) patch(id, { phase: 'error', error: errorMessage(reason), rateLimited: isRateLimited(reason) });
          if (isRateLimited(reason)) break;
        }
      }
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function pick() {
    if (pickingRef.current || busyRef.current) return;
    pickingRef.current = true; setPicking(true); setError(undefined);
    try {
      // Launch directly from the press event on web. The system photo picker needs no broad library permission.
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'], allowsMultipleSelection: true, selectionLimit: 10,
        allowsEditing: false, preferredAssetRepresentationMode: ImagePicker.UIImagePickerPreferredAssetRepresentationMode.Current,
      });
      if (result.canceled || !mounted.current) return;
      const added: UploadItem[] = [];
      const errors: string[] = [];
      for (const asset of result.assets.slice(0, 10)) {
        try {
          const file = preparePhoto(asset);
          if ([...queue.current, ...added].some(item => item.file.uri === file.uri)) continue;
          added.push({ id: `photo-${++sequence.current}`, file, phase: 'queued', progress: 0, checkpoint: {} });
        } catch (reason) { errors.push(`${asset.fileName ?? '선택한 사진'}: ${errorMessage(reason)}`); }
      }
      if (result.assets.length > 10) errors.push('한 번에 10장까지 추가할 수 있어요. 나머지 사진은 다시 선택해 주세요.');
      if (errors.length) setError(errors.join('\n'));
      commit([...queue.current, ...added]);
      void run(added.map(item => item.id));
    } catch { if (mounted.current) setError('사진을 선택하지 못했어요. 사진 접근 설정을 확인하고 다시 시도해 주세요.'); }
    finally { pickingRef.current = false; if (mounted.current) setPicking(false); }
  }

  function close() {
    if (busyRef.current || pickingRef.current) return;
    if (!queue.current.some(item => item.phase !== 'success')) return onClose();
    const message = '완료되지 않은 사진은 이 화면을 닫으면 다시 선택해야 해요. 닫을까요?';
    if (Platform.OS === 'web') { if (globalThis.confirm?.(message)) onClose(); }
    else Alert.alert('사진 업로드', message, [{ text: '계속하기', style: 'cancel' }, { text: '닫기', onPress: onClose }]);
  }

  const waiting = items.filter(item => item.phase === 'error' || item.phase === 'queued');
  const finished = items.filter(item => item.phase === 'success').length;
  return <Modal visible animationType="slide" onRequestClose={close}>
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}><Text accessibilityRole="header" style={styles.heading}>기록에 사진 추가</Text><Pressable accessibilityRole="button" accessibilityLabel="사진 업로드 화면 닫기" hitSlop={10} disabled={busy || picking} onPress={close}><Text style={[styles.link, (busy || picking) && styles.disabled]}>닫기</Text></Pressable></View>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.help}>한 번에 최대 10장 · 사진 한 장당 10MB 이하</Text>
        <Pressable accessibilityRole="button" accessibilityLabel="업로드할 사진 선택" accessibilityState={{ disabled: busy || picking, busy: picking }} disabled={busy || picking} onPress={() => void pick()} style={[styles.select, (busy || picking) && styles.disabled]}><Text style={styles.selectText}>{picking ? '사진 선택 중…' : '＋ 사진 선택'}</Text></Pressable>
        {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
        {items.length ? <Text style={styles.summary}>사진 {items.length}장 중 {finished}장 완료</Text> : <Text style={styles.help}>사진을 선택하면 이 기록에 업로드합니다.</Text>}
        {items.map(item => <View key={item.id} style={styles.card}>
          <View style={styles.row}><Image source={{ uri: item.file.uri }} style={styles.photo} contentFit="cover" /><View style={styles.detail}>
            <Text numberOfLines={2} style={styles.fileName}>{item.file.originalFileName}</Text>
            <Text style={styles.help}>{(item.file.fileSize / 1024 / 1024).toFixed(1)}MB</Text>
            <Text accessibilityLiveRegion="polite" style={[styles.status, item.phase === 'success' && styles.success, item.phase === 'error' && styles.failure]}>{item.rateLimited ? '요청 제한 · 잠시 대기' : labels[item.phase]}{item.phase === 'uploading' ? ` ${item.progress}%` : ''}</Text>
          </View>{['signing', 'uploading', 'completing'].includes(item.phase) ? <ActivityIndicator color="#208AEF" /> : null}</View>
          {item.phase === 'uploading' || item.phase === 'completing' ? <View accessibilityRole="progressbar" accessibilityLabel={`${item.file.originalFileName} 업로드`} accessibilityValue={{ min: 0, max: 100, now: item.progress }} style={styles.track}><View style={[styles.fill, { width: `${item.progress}%` }]} /></View> : null}
          {item.error ? <Text style={styles.error}>{item.error}</Text> : null}
          {item.phase === 'error' || item.phase === 'queued' ? <Pressable accessibilityRole="button" disabled={busy || picking} onPress={() => void run([item.id])} style={styles.retry}><Text style={[styles.link, (busy || picking) && styles.disabled]}>{item.checkpoint.putSucceeded ? '완료 처리 다시 시도' : '업로드 다시 시도'}</Text></Pressable> : null}
        </View>)}
        {!busy && waiting.length > 1 ? <Pressable accessibilityRole="button" disabled={picking} onPress={() => void run(waiting.map(item => item.id))}><Text style={styles.link}>완료되지 않은 사진 모두 다시 시도</Text></Pressable> : null}
        {busy ? <View style={styles.row}><Text style={[styles.help, styles.detail]}>업로드가 끝날 때까지 화면을 유지해 주세요.</Text><Pressable accessibilityRole="button" onPress={() => controller.current?.abort()}><Text style={styles.link}>중단</Text></Pressable></View> : null}
      </ScrollView>
    </SafeAreaView>
  </Modal>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' },
  header: { padding: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#FFF' },
  heading: { fontSize: 18, fontWeight: '800', color: '#17212B' },
  content: { padding: 22, gap: 14, paddingBottom: 50 }, title: { fontSize: 22, fontWeight: '800', color: '#17212B' },
  help: { fontSize: 13, color: '#65717E', lineHeight: 20 }, link: { color: '#1677D2', fontWeight: '700', paddingVertical: 6 },
  select: { backgroundColor: '#208AEF', borderRadius: 11, padding: 15, alignItems: 'center' }, selectText: { color: '#FFF', fontWeight: '800' },
  disabled: { opacity: 0.45 }, summary: { color: '#33404D', fontWeight: '700' },
  card: { backgroundColor: '#FFF', borderRadius: 14, padding: 14, borderWidth: 1, borderColor: '#E7EBEF', gap: 10 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 }, photo: { width: 64, height: 64, borderRadius: 8 }, detail: { flex: 1 },
  fileName: { color: '#33404D', fontWeight: '700' }, status: { color: '#1677D2', fontSize: 13, marginTop: 4 },
  success: { color: '#15803D' }, failure: { color: '#B42318' }, error: { color: '#B42318', backgroundColor: '#FFF0F0', borderRadius: 8, padding: 10 },
  track: { height: 6, backgroundColor: '#E7EBEF', borderRadius: 3, overflow: 'hidden' }, fill: { height: 6, backgroundColor: '#208AEF' },
  retry: { alignSelf: 'flex-start' },
});
