package com.tripdiary.book;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import com.tripdiary.book.DiaryTemplate.*;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class RuleBasedDiaryGeneratorTest {
    final RuleBasedDiaryGenerator generator = new RuleBasedDiaryGenerator();
    final LocalDate date = LocalDate.of(2026, 11, 1);
    final Instant instant = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void classicUsesTextThenTwoPhotosPerPageAndPhotoUsesCombinedThenSinglePhotos() {
        EntrySnapshot entry = entry(1, date, "memory", photos(5));
        var classic = generate(TemplateType.CLASSIC, null, List.of(entry));
        var photo = generate(TemplateType.PHOTO, null, List.of(entry));
        assertThat(classic.pages()).extracting(PagePlan::layoutType)
                .containsExactly(LayoutType.COVER, LayoutType.TEXT, LayoutType.PHOTO_GRID, LayoutType.PHOTO_GRID, LayoutType.PHOTO_GRID);
        assertThat(photo.pages()).extracting(PagePlan::layoutType)
                .containsExactly(LayoutType.COVER, LayoutType.PHOTO_TEXT, LayoutType.PHOTO_GRID, LayoutType.PHOTO_GRID, LayoutType.PHOTO_GRID, LayoutType.PHOTO_GRID);
        assertThat(classic.pages().get(2).content().get("imageIds")).isEqualTo(List.of(id(100).toString(), id(101).toString()));
        assertThat(photo.pages().get(1).content().get("text")).isEqualTo("memory");
    }

    @Test
    void orderIsIndependentOfSourceOrderIncludingTiedTimestamps() {
        var photos = new ArrayList<>(photos(3)); Collections.reverse(photos);
        EntrySnapshot first = entry(1, date, "first", photos);
        EntrySnapshot tied = entry(2, date, "second", List.of());
        EntrySnapshot next = entry(3, date.plusDays(1), "next day", List.of());
        var one = generate(TemplateType.CLASSIC, null, List.of(next, tied, first));
        var two = generate(TemplateType.CLASSIC, null, List.of(first, next, tied));
        assertThat(one).isEqualTo(two);
        assertThat(one.coverImageId()).isEqualTo(id(100));
        assertThat(one.pages()).extracting(page -> page.content().get("text"))
                .containsExactly(date + " — " + date.plusDays(2), "first", "", "", "second", "next day");
    }

    @Test
    void explicitCoverWinsAndUnknownCoverIsRejected() {
        var entry = entry(1, date, "memory", photos(2));
        assertThat(generate(TemplateType.CLASSIC, id(101), List.of(entry)).coverImageId()).isEqualTo(id(101));
        assertThatThrownBy(() -> generate(TemplateType.CLASSIC, id(999), List.of(entry)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DIARY_COVER_INVALID));
    }

    @Test
    void emptyTripHasOnlyCoverAndBlankTextDoesNotCreateEmptyEntryPages() {
        for (TemplateType template : TemplateType.values()) {
            var empty = generate(template, null, List.of());
            assertThat(empty.pages()).hasSize(1);
            assertThat(empty.coverImageId()).isNull();
            var blank = generate(template, null, List.of(entry(1, date, "  ", List.of()), entry(2, date.plusDays(1), null, photos(1))));
            assertThat(blank.pages()).extracting(PagePlan::pageType).containsExactly(PageType.COVER, PageType.PHOTOS);
        }
    }

    @Test
    void textOnlyTripsWorkInBothTemplatesAndLongUnicodeTextIsNotLost() {
        String text = "여행 🌊\n".repeat(600);
        for (TemplateType template : TemplateType.values()) {
            var result = generate(template, null, List.of(entry(1, date, text, List.of())));
            assertThat(result.coverImageId()).isNull();
            assertThat(result.pages().subList(1, result.pages().size())).allMatch(page -> page.layoutType() == LayoutType.TEXT);
            String joined = result.pages().stream().skip(1).map(page -> (String) page.content().get("text")).reduce("", String::concat);
            assertThat(joined).isEqualTo(text);
            assertThat(result.pages().stream().skip(1).map(page -> (String) page.content().get("text")))
                    .allMatch(chunk -> chunk.codePointCount(0, chunk.length()) <= 1200);
        }
    }

    @Test
    void excessivePagesAreRejectedRatherThanTruncated() {
        assertThatThrownBy(() -> generate(TemplateType.PHOTO, null, List.of(entry(1, date, "text", photos(501)))))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DIARY_TOO_LARGE));
    }

    private RuleBasedDiaryGenerator.Plan generate(TemplateType type, UUID cover, List<EntrySnapshot> entries) {
        return generator.generate("Trip", date, date.plusDays(2), type, cover, entries);
    }
    private EntrySnapshot entry(int id, LocalDate day, String text, List<PhotoSnapshot> photos) {
        return new EntrySnapshot(id(id), day, instant, "Title", text, photos);
    }
    private List<PhotoSnapshot> photos(int count) {
        List<PhotoSnapshot> photos = new ArrayList<>();
        for (int i = 0; i < count; i++) photos.add(new PhotoSnapshot(id(100 + i), instant));
        return photos;
    }
    private UUID id(int value) { return new UUID(0, value); }
}
