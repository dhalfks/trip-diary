import { useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

type Props = {
  label: string;
  value: string;
  onChange(value: string): void;
  minimumDate?: string;
  disabled?: boolean;
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function parseDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function formatDate(date: Date) {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}

function displayDate(value: string) {
  const date = parseDate(value);
  return `${date.getUTCFullYear()}년 ${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`;
}

export function CalendarDatePicker({ label, value, onChange, minimumDate, disabled }: Props) {
  const selected = useMemo(() => parseDate(value), [value]);
  const [visible, setVisible] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(Date.UTC(selected.getUTCFullYear(), selected.getUTCMonth(), 1)));
  const minimum = minimumDate ? parseDate(minimumDate) : undefined;
  const calendarDays = useMemo(() => {
    const year = visibleMonth.getUTCFullYear();
    const month = visibleMonth.getUTCMonth();
    const firstWeekday = new Date(Date.UTC(year, month, 1)).getUTCDay();
    return Array.from({ length: 42 }, (_, index) => new Date(Date.UTC(year, month, index - firstWeekday + 1)));
  }, [visibleMonth]);

  function open() {
    setVisibleMonth(new Date(Date.UTC(selected.getUTCFullYear(), selected.getUTCMonth(), 1)));
    setVisible(true);
  }

  function moveMonth(amount: number) {
    setVisibleMonth(current => new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + amount, 1)));
  }

  function choose(date: Date) {
    if (minimum && date < minimum) return;
    onChange(formatDate(date));
    setVisible(false);
  }

  return <>
    <Pressable accessibilityRole="button" accessibilityLabel={`${label}, ${displayDate(value)}`} disabled={disabled} onPress={open} style={[styles.trigger, disabled && styles.disabled]}>
      <Text style={styles.calendarIcon}>▣</Text><Text style={styles.value}>{value}</Text><Text style={styles.arrow}>⌄</Text>
    </Pressable>
    <Modal visible={visible} transparent animationType="fade" onRequestClose={() => setVisible(false)}>
      <SafeAreaView style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => setVisible(false)} />
        <View style={styles.modal}>
          <View style={styles.modalHeader}><Pressable hitSlop={12} onPress={() => moveMonth(-1)}><Text style={styles.nav}>‹</Text></Pressable><Text style={styles.monthTitle}>{visibleMonth.getUTCFullYear()}년 {visibleMonth.getUTCMonth() + 1}월</Text><Pressable hitSlop={12} onPress={() => moveMonth(1)}><Text style={styles.nav}>›</Text></Pressable></View>
          <View style={styles.weekRow}>{WEEKDAYS.map((weekday, index) => <Text key={weekday} style={[styles.weekday, index === 0 && styles.sunday]}>{weekday}</Text>)}</View>
          <View style={styles.grid}>{calendarDays.map(date => {
            const dateValue = formatDate(date);
            const outside = date.getUTCMonth() !== visibleMonth.getUTCMonth();
            const unavailable = Boolean(minimum && date < minimum);
            const isSelected = dateValue === value;
            return <Pressable accessibilityRole="button" accessibilityLabel={displayDate(dateValue)} accessibilityState={{ selected: isSelected, disabled: unavailable }} key={dateValue} disabled={unavailable} onPress={() => choose(date)} style={[styles.day, isSelected && styles.selectedDay]}>
              <Text style={[styles.dayText, outside && styles.outside, unavailable && styles.unavailable, isSelected && styles.selectedText]}>{date.getUTCDate()}</Text>
            </Pressable>;
          })}</View>
          <Pressable onPress={() => setVisible(false)} style={styles.close}><Text style={styles.closeText}>취소</Text></Pressable>
        </View>
      </SafeAreaView>
    </Modal>
  </>;
}

const styles = StyleSheet.create({
  trigger: { height: 52, borderWidth: 1, borderColor: '#D7DBE0', borderRadius: 12, paddingHorizontal: 13, backgroundColor: '#FFF', flexDirection: 'row', alignItems: 'center', gap: 8 },
  disabled: { opacity: 0.55 }, calendarIcon: { color: '#208AEF', fontSize: 17 }, value: { flex: 1, color: '#17212B', fontSize: 15, fontWeight: '600' }, arrow: { color: '#65717E', fontSize: 18 },
  overlay: { flex: 1, backgroundColor: 'rgba(23,33,43,0.48)', alignItems: 'center', justifyContent: 'center', padding: 20 },
  modal: { width: '100%', maxWidth: 390, borderRadius: 20, backgroundColor: '#FFF', padding: 18 },
  modalHeader: { height: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, nav: { width: 42, color: '#1677D2', fontSize: 32, lineHeight: 36, textAlign: 'center' }, monthTitle: { color: '#17212B', fontSize: 18, fontWeight: '800' },
  weekRow: { flexDirection: 'row', marginTop: 12 }, weekday: { width: '14.2857%', color: '#65717E', textAlign: 'center', fontSize: 12, fontWeight: '700' }, sunday: { color: '#D92D20' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 5 }, day: { width: '14.2857%', aspectRatio: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 99 }, selectedDay: { backgroundColor: '#208AEF' }, dayText: { color: '#17212B', fontSize: 15, fontWeight: '600' }, outside: { color: '#B4BDC6' }, unavailable: { color: '#D7DBE0' }, selectedText: { color: '#FFF', fontWeight: '800' },
  close: { alignSelf: 'flex-end', paddingHorizontal: 14, paddingVertical: 10, marginTop: 8 }, closeText: { color: '#65717E', fontWeight: '700' },
});
