package com.tripdiary.itinerary;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ItineraryResponse(UUID id, UUID tripDayId, String title, String notes, LocalTime startTime, LocalTime endTime, int sortOrder, PlaceResponse place, Instant createdAt, Instant updatedAt) {
    static ItineraryResponse from(Itinerary item) {
        return new ItineraryResponse(item.getId(), item.getTripDay().getId(), item.getTitle(), item.getNotes(), item.getStartTime(), item.getEndTime(), item.getSortOrder(), item.getPlace() == null ? null : PlaceResponse.from(item.getPlace()), item.getCreatedAt(), item.getUpdatedAt());
    }
}
