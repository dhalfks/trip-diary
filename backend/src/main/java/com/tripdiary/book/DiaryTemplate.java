package com.tripdiary.book;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Template input/output is independent of JPA, storage URLs, and the mobile renderer. */
public interface DiaryTemplate {
    List<PagePlan> entryPages(EntrySnapshot entry);

    record PhotoSnapshot(UUID id, Instant createdAt) {}
    record EntrySnapshot(UUID id, LocalDate date, Instant createdAt, String title, String text, List<PhotoSnapshot> photos) {}
    record PagePlan(PageType pageType, LayoutType layoutType, Map<String, Object> content) {}
}
