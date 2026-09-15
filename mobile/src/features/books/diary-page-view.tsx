import { Image } from 'expo-image';
import { useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import type { DiaryImage } from '@/features/images/types';
import type { pageModel } from './book-model';
import type { TemplateType } from './types';

export function DiaryPageView({ page, template, revision, onRefresh }: {
  page: ReturnType<typeof pageModel>; template: TemplateType; revision: number; onRefresh(): void;
}) {
  const cover = page.layout === 'COVER';
  return <View style={[styles.paper, template === 'PHOTO' && styles.photoPaper]}>
    <Text style={styles.eyebrow}>{cover ? 'TRAVEL DIARY' : page.date}</Text>
    <Text selectable style={[styles.title, cover && styles.coverTitle]}>{page.title}</Text>
    <View style={styles.rule} />
    {page.photos.length ? <View style={styles.photos}>{page.photos.map(photo =>
      <PagePhoto key={`${photo.id}:${revision}`} image={photo.image}
        tall={cover || (page.layout === 'PHOTO_GRID' && page.photos.length === 1)} onRefresh={onRefresh} />)}</View> : cover ? <View style={styles.noCover}><Text style={styles.noCoverText}>나의 여행 이야기</Text></View> : null}
    {page.text ? <Text selectable style={[styles.body, cover && styles.period]}>{page.text}</Text> : null}
  </View>;
}

function PagePhoto({ image, tall, onRefresh }: { image?: DiaryImage; tall: boolean; onRefresh(): void }) {
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(!!image);
  return <View style={[styles.photoFrame, tall && styles.tall]}>
    {image ? <Image source={{ uri: image.downloadUrl }} accessibilityLabel={image.originalFileName} cachePolicy="none"
      contentFit="contain" style={styles.photo} onLoad={() => setLoading(false)} onError={() => { setFailed(true); setLoading(false); }} /> : null}
    {loading ? <View style={styles.overlay}><ActivityIndicator color="#208AEF" /></View> : null}
    {!image || failed ? <Pressable accessibilityRole="button" accessibilityLabel="사진 다시 불러오기" style={styles.overlay} onPress={onRefresh}>
      <Text style={styles.missing}>사진을 사용할 수 없어요</Text><Text style={styles.retry}>사진 다시 불러오기</Text>
    </Pressable> : null}
  </View>;
}

const styles = StyleSheet.create({
  paper: { backgroundColor: '#FFFCF5', borderRadius: 4, padding: 24, minHeight: 500, borderWidth: 1, borderColor: '#E6DFD1', gap: 16 },
  photoPaper: { backgroundColor: '#FFF', borderColor: '#D9E6F1' }, eyebrow: { color: '#8A745D', fontSize: 12, letterSpacing: 2, fontWeight: '700' },
  title: { color: '#263342', fontWeight: '700', fontSize: 23, lineHeight: 32, fontFamily: Platform.OS === 'ios' ? 'Georgia' : 'serif' },
  coverTitle: { fontSize: 32, lineHeight: 42, marginTop: 14 }, rule: { width: 44, height: 2, backgroundColor: '#B99B75' },
  body: { fontSize: 16, lineHeight: 28, color: '#3E4B59' }, period: { textAlign: 'center', color: '#718096', fontSize: 14 },
  photos: { gap: 12 }, photoFrame: { height: 190, backgroundColor: '#EDF2F7', overflow: 'hidden', borderRadius: 4 }, tall: { height: 300 },
  photo: { width: '100%', height: '100%' }, overlay: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, alignItems: 'center', justifyContent: 'center', gap: 8 },
  missing: { color: '#718096', fontSize: 13 }, retry: { color: '#1677D2', fontSize: 13, fontWeight: '700' },
  noCover: { minHeight: 230, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#DFD5C5' }, noCoverText: { color: '#A18D74', fontSize: 18 },
});
