package com.tripdiary.itinerary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlaceResponse(UUID id, String name, String address, BigDecimal latitude, BigDecimal longitude, Instant createdAt, Instant updatedAt) {
    static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getId(), place.getName(), place.getAddress(), place.getLatitude(), place.getLongitude(), place.getCreatedAt(), place.getUpdatedAt());
    }
}
