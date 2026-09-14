import type { DiaryImage } from '@/features/images/types';

export type TemplateType = 'CLASSIC' | 'PHOTO';
export type GenerateDiary = { title: string | null; templateType: TemplateType; coverImageId: string | null };
export type TravelDiary = {
  id: string; tripId: string; title: string; templateType: TemplateType; coverImageId: string | null;
  status: 'READY'; pageCount: number; createdAt: string; updatedAt: string;
};
export type DiaryPage = {
  id: string; pageOrder: number; pageType: 'COVER' | 'ENTRY' | 'PHOTOS';
  layoutType: 'COVER' | 'TEXT' | 'PHOTO_TEXT' | 'PHOTO_GRID';
  content: Record<string, unknown>; createdAt: string; updatedAt: string;
};
export type DiaryDetail = { diary: TravelDiary; pages: DiaryPage[]; images: DiaryImage[] };
export type BookApi = {
  list(): Promise<TravelDiary[]>;
  create(input: GenerateDiary): Promise<TravelDiary>;
  remove(id: string): Promise<void>;
};
