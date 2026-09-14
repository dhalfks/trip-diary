package com.tripdiary.book;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.tripdiary.image.ImageViewResponse;

public record TravelDiaryResponse(UUID id, UUID tripId, String title, TemplateType templateType,
                                   UUID coverImageId, DiaryStatus status, int pageCount, Instant createdAt, Instant updatedAt) {
    static TravelDiaryResponse from(TravelDiary diary) {
        return new TravelDiaryResponse(diary.getId(), diary.getTrip().getId(), diary.getTitle(), diary.getTemplateType(),
                diary.getCoverImageId(), diary.getStatus(), diary.getPages().size(), diary.getCreatedAt(), diary.getUpdatedAt());
    }
    public record PageResponse(UUID id, int pageOrder, PageType pageType, LayoutType layoutType,
                               Map<String, Object> content, Instant createdAt, Instant updatedAt) {
        static PageResponse from(DiaryPage page) {
            return new PageResponse(page.getId(), page.getPageOrder(), page.getPageType(), page.getLayoutType(),
                    page.getContent(), page.getCreatedAt(), page.getUpdatedAt());
        }
    }
    public record Detail(TravelDiaryResponse diary, List<PageResponse> pages, List<ImageViewResponse> images) {}
}
