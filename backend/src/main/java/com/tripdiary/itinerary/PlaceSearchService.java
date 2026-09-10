package com.tripdiary.itinerary;

import java.math.BigDecimal;
import java.util.*;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.trip.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Service
public class PlaceSearchService {
    private final TripRepository trips;
    private final RestClient client;
    private final String apiKey;

    public PlaceSearchService(
            TripRepository trips,
            @Value("${app.kakao.local-api-url:https://dapi.kakao.com}") String apiUrl,
            @Value("${app.kakao.rest-api-key:}") String apiKey) {
        this.trips = trips;
        this.client = RestClient.builder().baseUrl(apiUrl).build();
        this.apiKey = apiKey;
    }

    public List<PlaceSearchResult> search(UUID userId, UUID tripId, String query) {
        if (!trips.existsByIdAndUserId(tripId, userId)) throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        if (apiKey.isBlank()) throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
        try {
            KakaoResponse response = client.get()
                    .uri(builder -> builder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query.strip()).queryParam("size", 10).build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .retrieve().body(KakaoResponse.class);
            if (response == null || response.documents() == null) return List.of();
            return response.documents().stream().map(document -> new PlaceSearchResult(
                    document.id(), document.placeName(), preferredAddress(document),
                    new BigDecimal(document.y()), new BigDecimal(document.x()))).toList();
        } catch (RestClientException | NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PLACE_SEARCH_UNAVAILABLE);
        }
    }

    private String preferredAddress(KakaoDocument document) {
        return document.roadAddressName() == null || document.roadAddressName().isBlank()
                ? document.addressName() : document.roadAddressName();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record KakaoResponse(List<KakaoDocument> documents) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record KakaoDocument(String id, String placeName, String addressName, String roadAddressName, String x, String y) {}
}
