package com.tripdiary.itinerary;

import java.math.BigDecimal;

public record PlaceSearchResult(
        String providerId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude) {}
