package com.tripdiary.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        List<DayResponse> days,
        Instant createdAt,
        Instant updatedAt) {

    static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(), trip.getTitle(), trip.getStartDate(), trip.getEndDate(), trip.getTimezone(),
                trip.getDays().stream().map(DayResponse::from).toList(),
                trip.getCreatedAt(), trip.getUpdatedAt());
    }

    public record DayResponse(UUID id, LocalDate date, int dayNumber) {
        static DayResponse from(TripDay day) {
            return new DayResponse(day.getId(), day.getDate(), day.getDayNumber());
        }
    }
}
