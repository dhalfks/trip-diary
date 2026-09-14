package com.tripdiary.book;

import java.time.LocalDate;
import java.util.*;
import com.tripdiary.book.DiaryTemplate.*;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedDiaryGenerator {
    public static final int MAX_PAGES = 500;
    private static final int TEXT_CODE_POINTS = 1200;
    private final Map<TemplateType, DiaryTemplate> templates = Map.of(
            TemplateType.CLASSIC, entry -> entryPages(entry, false),
            TemplateType.PHOTO, entry -> entryPages(entry, true));

    public Plan generate(String title, LocalDate startDate, LocalDate endDate, TemplateType type,
                         UUID requestedCover, List<EntrySnapshot> source) {
        List<EntrySnapshot> entries = source.stream()
                .sorted(Comparator.comparing(EntrySnapshot::date).thenComparing(EntrySnapshot::createdAt).thenComparing(entry -> entry.id().toString()))
                .map(entry -> new EntrySnapshot(entry.id(), entry.date(), entry.createdAt(), entry.title(), entry.text(),
                        entry.photos().stream().sorted(Comparator.comparing(PhotoSnapshot::createdAt).thenComparing(photo -> photo.id().toString())).toList()))
                .toList();
        List<UUID> imageIds = entries.stream().flatMap(entry -> entry.photos().stream()).map(PhotoSnapshot::id).toList();
        if (requestedCover != null && !imageIds.contains(requestedCover)) throw new BusinessException(ErrorCode.DIARY_COVER_INVALID);
        UUID cover = requestedCover != null ? requestedCover : imageIds.stream().findFirst().orElse(null);
        List<PagePlan> pages = new ArrayList<>();
        pages.add(page(PageType.COVER, LayoutType.COVER, title, startDate + " — " + endDate, "", cover == null ? List.of() : List.of(cover)));
        for (EntrySnapshot entry : entries) {
            pages.addAll(templates.get(type).entryPages(entry));
            if (pages.size() > MAX_PAGES) throw new BusinessException(ErrorCode.DIARY_TOO_LARGE);
        }
        return new Plan(cover, List.copyOf(pages));
    }

    private static List<PagePlan> entryPages(EntrySnapshot entry, boolean photoFirst) {
        List<PagePlan> pages = new ArrayList<>();
        List<String> text = chunks(entry.text());
        List<UUID> images = entry.photos().stream().map(PhotoSnapshot::id).toList();
        int photoOffset = 0;
        for (int i = 0; i < text.size(); i++) {
            boolean withPhoto = photoFirst && i == 0 && !images.isEmpty();
            pages.add(page(PageType.ENTRY, withPhoto ? LayoutType.PHOTO_TEXT : LayoutType.TEXT,
                    entry.title(), text.get(i), entry.date().toString(), withPhoto ? List.of(images.get(0)) : List.of()));
            if (withPhoto) photoOffset = 1;
        }
        int perPage = photoFirst ? 1 : 2;
        for (int i = photoOffset; i < images.size(); i += perPage) {
            pages.add(page(PageType.PHOTOS, LayoutType.PHOTO_GRID, entry.title(), "", entry.date().toString(),
                    images.subList(i, Math.min(i + perPage, images.size()))));
        }
        return pages;
    }

    private static List<String> chunks(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        for (int offset = 0; offset < value.length();) {
            int end = value.offsetByCodePoints(offset, Math.min(TEXT_CODE_POINTS, value.codePointCount(offset, value.length())));
            chunks.add(value.substring(offset, end)); offset = end;
        }
        return chunks;
    }

    private static PagePlan page(PageType type, LayoutType layout, String title, String text, String date, List<UUID> images) {
        return new PagePlan(type, layout, Map.of("schemaVersion", 1, "title", title == null ? "" : title,
                "text", text, "date", date, "imageIds", images.stream().map(UUID::toString).toList(), "layout", layout.name()));
    }

    public record Plan(UUID coverImageId, List<PagePlan> pages) {}
}
