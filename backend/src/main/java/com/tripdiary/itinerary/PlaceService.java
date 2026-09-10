package com.tripdiary.itinerary;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class PlaceService {
    private final PlaceRepository places; private final TripRepository trips; private final ItineraryRepository itineraries;
    public PlaceService(PlaceRepository places, TripRepository trips, ItineraryRepository itineraries) { this.places = places; this.trips = trips; this.itineraries = itineraries; }
    public List<PlaceResponse> list(UUID userId, UUID tripId) { ownedTrip(userId, tripId); return places.findAllByTripIdOrderByNameAscIdAsc(tripId).stream().map(PlaceResponse::from).toList(); }
    @Transactional public PlaceResponse create(UUID userId, UUID tripId, PlaceCommand command) {
        Trip trip = ownedTrip(userId, tripId); ValidatedPlace value = validate(command);
        return PlaceResponse.from(places.save(new Place(trip, value.name(), value.address(), value.latitude(), value.longitude())));
    }
    @Transactional public PlaceResponse update(UUID userId, UUID tripId, UUID placeId, PlaceCommand command) {
        ownedTrip(userId, tripId); Place place = find(tripId, placeId); ValidatedPlace value = validate(command);
        place.change(value.name(), value.address(), value.latitude(), value.longitude()); return PlaceResponse.from(place);
    }
    @Transactional public void delete(UUID userId, UUID tripId, UUID placeId) {
        ownedTrip(userId, tripId); Place place = find(tripId, placeId);
        itineraries.clearPlace(placeId); places.delete(place);
    }
    private Trip ownedTrip(UUID userId, UUID tripId) { return trips.findByIdAndUserId(tripId, userId).orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND)); }
    private Place find(UUID tripId, UUID placeId) { return places.findByIdAndTripId(placeId, tripId).orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND)); }
    private ValidatedPlace validate(PlaceCommand command) {
        if ((command.latitude() == null) != (command.longitude() == null)) throw new BusinessException(ErrorCode.INVALID_PLACE_COORDINATES);
        return new ValidatedPlace(command.name().strip(), blankToNull(command.address()), command.latitude(), command.longitude());
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    public record PlaceCommand(String name, String address, BigDecimal latitude, BigDecimal longitude) {}
    private record ValidatedPlace(String name, String address, BigDecimal latitude, BigDecimal longitude) {}
}
