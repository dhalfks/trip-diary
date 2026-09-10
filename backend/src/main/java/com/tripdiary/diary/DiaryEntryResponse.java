package com.tripdiary.diary;

import java.time.Instant;
import java.util.UUID;

public record DiaryEntryResponse(
        UUID id, UUID tripDayId, String title, String content,
        LinkResponse itinerary, LinkResponse place, Instant createdAt, Instant updatedAt) {
    static DiaryEntryResponse from(DiaryEntry entry) {
        return new DiaryEntryResponse(
                entry.getId(), entry.getTripDay().getId(), entry.getTitle(), entry.getContent(),
                entry.getItinerary() == null ? null : new LinkResponse(entry.getItinerary().getId(), entry.getItinerary().getTitle()),
                entry.getPlace() == null ? null : new LinkResponse(entry.getPlace().getId(), entry.getPlace().getName()),
                entry.getCreatedAt(), entry.getUpdatedAt());
    }

    public record LinkResponse(UUID id, String name) {}
}
