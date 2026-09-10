import { useMemo, useState } from 'react';
import { FlatList, Modal, Pressable, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';

const TIMEZONES = [
  ['Asia/Seoul', '서울 · 대한민국'], ['Asia/Tokyo', '도쿄 · 일본'], ['Asia/Shanghai', '상하이 · 중국'],
  ['Asia/Hong_Kong', '홍콩'], ['Asia/Taipei', '타이베이 · 대만'], ['Asia/Singapore', '싱가포르'],
  ['Asia/Bangkok', '방콕 · 태국'], ['Asia/Ho_Chi_Minh', '호찌민 · 베트남'], ['Asia/Manila', '마닐라 · 필리핀'],
  ['Asia/Kuala_Lumpur', '쿠알라룸푸르 · 말레이시아'], ['Asia/Jakarta', '자카르타 · 인도네시아'],
  ['Asia/Dubai', '두바이 · 아랍에미리트'], ['Asia/Kolkata', '인도'], ['Australia/Sydney', '시드니 · 호주'],
  ['Australia/Melbourne', '멜버른 · 호주'], ['Pacific/Auckland', '오클랜드 · 뉴질랜드'],
  ['Europe/London', '런던 · 영국'], ['Europe/Paris', '파리 · 프랑스'], ['Europe/Rome', '로마 · 이탈리아'],
  ['Europe/Madrid', '마드리드 · 스페인'], ['Europe/Berlin', '베를린 · 독일'], ['Europe/Amsterdam', '암스테르담 · 네덜란드'],
  ['Europe/Prague', '프라하 · 체코'], ['Europe/Athens', '아테네 · 그리스'], ['Europe/Istanbul', '이스탄불 · 튀르키예'],
  ['America/New_York', '뉴욕 · 미국'], ['America/Chicago', '시카고 · 미국'], ['America/Denver', '덴버 · 미국'],
  ['America/Los_Angeles', '로스앤젤레스 · 미국'], ['America/Vancouver', '밴쿠버 · 캐나다'],
  ['America/Toronto', '토론토 · 캐나다'], ['Pacific/Honolulu', '하와이 · 미국'],
  ['America/Mexico_City', '멕시코시티 · 멕시코'], ['America/Sao_Paulo', '상파울루 · 브라질'],
  ['Africa/Cairo', '카이로 · 이집트'], ['Africa/Johannesburg', '요하네스버그 · 남아프리카공화국'],
  ['UTC', '협정 세계시'],
] as const;

type Props = { value: string; disabled?: boolean; onChange(value: string): void };

export function TimezonePicker({ value, disabled, onChange }: Props) {
  const [visible, setVisible] = useState(false);
  const [query, setQuery] = useState('');
  const options = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    if (!normalized) return TIMEZONES;
    return TIMEZONES.filter(([zone, label]) => `${zone} ${label}`.toLocaleLowerCase().includes(normalized));
  }, [query]);
  const selectedLabel = TIMEZONES.find(([zone]) => zone === value)?.[1];

  function close() { setVisible(false); setQuery(''); }
  return <>
    <Pressable disabled={disabled} onPress={() => setVisible(true)} style={({ pressed }) => [styles.field, pressed && styles.pressed, disabled && styles.disabled]}>
      <View><Text style={styles.value}>{selectedLabel ?? value}</Text><Text style={styles.zone}>{value}</Text></View>
      <Text style={styles.chevron}>⌄</Text>
    </Pressable>
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={close}>
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}><Pressable onPress={close} hitSlop={12}><Text style={styles.cancel}>취소</Text></Pressable><Text style={styles.title}>여행지 타임존</Text><View style={styles.spacer} /></View>
        <TextInput value={query} onChangeText={setQuery} placeholder="도시 또는 타임존 검색" autoCapitalize="none" autoCorrect={false} style={styles.search} />
        <FlatList data={options} keyExtractor={item => item[0]} keyboardShouldPersistTaps="handled"
          contentContainerStyle={options.length === 0 && styles.emptyContainer}
          ListEmptyComponent={<Text style={styles.empty}>검색 결과가 없습니다.</Text>}
          renderItem={({ item: [zone, label] }) => {
            const selected = zone === value;
            return <Pressable onPress={() => { onChange(zone); close(); }} style={({ pressed }) => [styles.option, pressed && styles.pressed]}>
              <View style={styles.optionText}><Text style={[styles.label, selected && styles.selected]}>{label}</Text><Text style={styles.optionZone}>{zone}</Text></View>
              {selected ? <Text style={styles.check}>✓</Text> : null}
            </Pressable>;
          }} />
      </SafeAreaView>
    </Modal>
  </>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, field: { minHeight: 58, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, paddingHorizontal: 14, paddingVertical: 9, backgroundColor: '#FFF', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  value: { color: '#17212B', fontSize: 16, fontWeight: '700' }, zone: { color: '#7A8794', fontSize: 12, marginTop: 2 }, chevron: { color: '#65717E', fontSize: 22 }, pressed: { opacity: 0.65 }, disabled: { opacity: 0.55 },
  header: { height: 58, paddingHorizontal: 20, backgroundColor: '#FFF', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', flexDirection: 'row', alignItems: 'center' }, cancel: { color: '#1677D2', fontSize: 16, fontWeight: '700' }, title: { flex: 1, color: '#17212B', fontSize: 18, fontWeight: '800', textAlign: 'center' }, spacer: { width: 35 },
  search: { height: 48, margin: 16, borderRadius: 12, paddingHorizontal: 15, backgroundColor: '#EAF0F5', color: '#17212B', fontSize: 16 },
  option: { minHeight: 66, paddingHorizontal: 22, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#E7EBEF', backgroundColor: '#FFF', flexDirection: 'row', alignItems: 'center' }, optionText: { flex: 1 }, label: { color: '#17212B', fontSize: 16, fontWeight: '700' }, selected: { color: '#1677D2' }, optionZone: { color: '#7A8794', fontSize: 13, marginTop: 3 }, check: { color: '#208AEF', fontSize: 20, fontWeight: '900' },
  emptyContainer: { flexGrow: 1 }, empty: { color: '#65717E', textAlign: 'center', marginTop: 60 },
});
