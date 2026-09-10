import { useMemo, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { errorMessage } from '@/lib/api';
import type { Trip, TripInput } from './types';
import { CalendarDatePicker } from './calendar-date-picker';
import { TimezonePicker } from './timezone-picker';

type Props = { mode: 'create' | 'edit'; initialTrip?: Trip; onSubmit(input: TripInput): Promise<Trip> };
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function localToday() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function isRealDate(value: string) {
  if (!DATE_PATTERN.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function TripFormScreen({ mode, initialTrip, onSubmit }: Props) {
  const defaultDate = useMemo(() => localToday(), []);
  const defaultTimezone = useMemo(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Seoul', []);
  const [title, setTitle] = useState(initialTrip?.title ?? '');
  const [startDate, setStartDate] = useState(initialTrip?.startDate ?? defaultDate);
  const [endDate, setEndDate] = useState(initialTrip?.endDate ?? defaultDate);
  const [timezone, setTimezone] = useState(initialTrip?.timezone ?? defaultTimezone);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  function changeStartDate(value: string) {
    setStartDate(value);
    if (endDate < value) setEndDate(value);
  }

  async function submit() {
    const cleanTitle = title.trim();
    const cleanTimezone = timezone.trim();
    if (!cleanTitle) return setError('여행 이름을 입력해 주세요.');
    if (cleanTitle.length > 100) return setError('여행 이름은 100자 이하로 입력해 주세요.');
    if (!isRealDate(startDate) || !isRealDate(endDate)) return setError('날짜를 YYYY-MM-DD 형식으로 입력해 주세요.');
    if (endDate < startDate) return setError('종료일은 시작일보다 빠를 수 없습니다.');
    if (!cleanTimezone) return setError('타임존을 입력해 주세요.');
    setBusy(true); setError(undefined);
    try {
      const trip = await onSubmit({ title: cleanTitle, startDate, endDate, timezone: cleanTimezone });
      router.replace({ pathname: '/trips/[tripId]', params: { tripId: trip.id } });
    } catch (reason) { setError(errorMessage(reason)); }
    finally { setBusy(false); }
  }

  return <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.header}>
        <Pressable onPress={() => router.back()} hitSlop={12}><Text style={styles.back}>‹ 뒤로</Text></Pressable>
        <Text style={styles.headerTitle}>{mode === 'create' ? '새 여행' : '여행 수정'}</Text><View style={styles.headerSpacer} />
      </View>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.label}>여행 이름</Text>
        <TextInput value={title} onChangeText={setTitle} editable={!busy} maxLength={100} placeholder="예: 제주도 여름휴가" style={styles.input} />
        <View style={styles.row}>
          <View style={styles.half}><Text style={styles.label}>시작일</Text><CalendarDatePicker label="시작일" value={startDate} onChange={changeStartDate} disabled={busy} /></View>
          <View style={styles.half}><Text style={styles.label}>종료일</Text><CalendarDatePicker label="종료일" value={endDate} onChange={setEndDate} minimumDate={startDate} disabled={busy} /></View>
        </View>
        <Text style={styles.label}>여행지 타임존</Text>
        <TimezonePicker value={timezone} onChange={setTimezone} disabled={busy} />
        <Text style={styles.hint}>여행 도시를 선택하면 현지 타임존이 자동으로 저장됩니다.</Text>
        {error ? <View style={styles.errorBox}><Text style={styles.errorText}>{error}</Text></View> : null}
        <Pressable disabled={busy} onPress={() => void submit()} style={[styles.submit, busy && styles.disabled]}>
          {busy ? <ActivityIndicator color="#FFF" /> : <Text style={styles.submitText}>{mode === 'create' ? '여행 만들기' : '변경사항 저장'}</Text>}
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#F7FAFC' }, flex: { flex: 1 },
  header: { height: 58, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#E7EBEF', backgroundColor: '#FFF' },
  back: { color: '#1677D2', fontSize: 16, fontWeight: '700' }, headerTitle: { flex: 1, color: '#17212B', fontSize: 18, fontWeight: '800', textAlign: 'center' }, headerSpacer: { width: 44 },
  content: { padding: 24, paddingBottom: 48, gap: 10 }, label: { color: '#33404D', fontSize: 14, fontWeight: '700', marginTop: 10 },
  input: { height: 52, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, paddingHorizontal: 14, backgroundColor: '#FFF', color: '#17212B', fontSize: 16 },
  row: { flexDirection: 'row', gap: 12 }, half: { flex: 1, gap: 10 }, hint: { color: '#7A8794', fontSize: 13, lineHeight: 19 },
  errorBox: { backgroundColor: '#FFF0F0', borderRadius: 10, marginTop: 10, padding: 13 }, errorText: { color: '#B42318', lineHeight: 20 },
  submit: { height: 54, marginTop: 14, alignItems: 'center', justifyContent: 'center', borderRadius: 14, backgroundColor: '#208AEF' }, submitText: { color: '#FFF', fontSize: 16, fontWeight: '800' }, disabled: { opacity: 0.55 },
});
