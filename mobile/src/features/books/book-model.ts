import type { DiaryImage } from '@/features/images/types';
import type { DiaryDetail, DiaryPage } from './types';

export function previewPage(detail: DiaryDetail | undefined, index: number) {
  if (!detail?.pages.length) return undefined;
  const page = detail.pages[Math.max(0, Math.min(index, detail.pages.length - 1))];
  return pageModel(page, detail.images);
}

export function pageModel(page: DiaryPage, images: DiaryImage[]) {
  const content = page.content ?? {};
  const text = (key: string) => typeof content[key] === 'string' ? content[key] as string : '';
  const ids = Array.isArray(content.imageIds) ? content.imageIds.filter((id): id is string => typeof id === 'string') : [];
  const byId = new Map(images.map(image => [image.id, image]));
  return { id: page.id, layout: page.layoutType, title: text('title'), text: text('text'), date: text('date'),
    photos: ids.map(id => ({ id, image: byId.get(id) })) };
}

type Day = { id: string; date: string };
type Entry = { id: string; createdAt: string };
/** Cover selection reuses entry/image endpoints with at most four requests in flight. */
export async function collectCoverImages(days: Day[], source: {
  entries(dayId: string): Promise<Entry[]>;
  images(dayId: string, entryId: string): Promise<DiaryImage[]>;
}) {
  const result: DiaryImage[] = [];
  const ordered = [...days].sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id));
  for (let i = 0; i < ordered.length; i += 4) {
    const groups = await Promise.all(ordered.slice(i, i + 4).map(async day => ({ day,
      entries: (await source.entries(day.id)).sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id)),
    })));
    for (const { day, entries } of groups) {
      for (let offset = 0; offset < entries.length; offset += 4) {
        const photos = await Promise.all(entries.slice(offset, offset + 4).map(entry => source.images(day.id, entry.id)));
        for (const group of photos) result.push(...[...group].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id)));
      }
    }
  }
  return result;
}
