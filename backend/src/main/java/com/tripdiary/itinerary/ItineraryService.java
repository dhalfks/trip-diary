package com.tripdiary.itinerary;

import java.time.LocalTime;
import java.util.*;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.trip.TripDay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class ItineraryService {
    private final ItineraryRepository itineraries; private final TripDayRepository days; private final PlaceRepository places;
    public ItineraryService(ItineraryRepository itineraries, TripDayRepository days, PlaceRepository places) { this.itineraries = itineraries; this.days = days; this.places = places; }
    public List<ItineraryResponse> list(UUID userId, UUID tripId, UUID dayId) { ownedDay(userId, tripId, dayId); return listEntities(dayId).stream().map(ItineraryResponse::from).toList(); }
    @Transactional public ItineraryResponse create(UUID userId, UUID tripId, UUID dayId, ItineraryCommand command) {
        TripDay day = ownedDay(userId, tripId, dayId); validateTime(command.startTime(), command.endTime()); Place place = resolvePlace(tripId, command.placeId());
        int order = listEntities(dayId).size();
        return ItineraryResponse.from(itineraries.save(new Itinerary(day, command.title().strip(), blankToNull(command.notes()), command.startTime(), command.endTime(), place, order)));
    }
    @Transactional public ItineraryResponse update(UUID userId, UUID tripId, UUID dayId, UUID itemId, ItineraryCommand command) {
        ownedDay(userId, tripId, dayId); validateTime(command.startTime(), command.endTime()); Itinerary item = find(dayId, itemId);
        item.change(command.title().strip(), blankToNull(command.notes()), command.startTime(), command.endTime(), resolvePlace(tripId, command.placeId())); return ItineraryResponse.from(item);
    }
    @Transactional public void delete(UUID userId, UUID tripId, UUID dayId, UUID itemId) {
        ownedDay(userId, tripId, dayId); itineraries.delete(find(dayId, itemId)); itineraries.flush(); normalize(dayId);
    }
    @Transactional public List<ItineraryResponse> reorder(UUID userId, UUID tripId, UUID dayId, List<UUID> orderedIds) {
        ownedDay(userId, tripId, dayId); List<Itinerary> current = listEntities(dayId);
        if (orderedIds.size() != current.size() || new HashSet<>(orderedIds).size() != orderedIds.size() || !current.stream().map(Itinerary::getId).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(orderedIds))) throw new BusinessException(ErrorCode.INVALID_ITINERARY_ORDER);
        Map<UUID, Itinerary> byId = new HashMap<>(); current.forEach(item -> byId.put(item.getId(), item));
        for (int i = 0; i < orderedIds.size(); i++) byId.get(orderedIds.get(i)).changeSortOrder(10_000 + i);
        itineraries.flush();
        for (int i = 0; i < orderedIds.size(); i++) byId.get(orderedIds.get(i)).changeSortOrder(i);
        itineraries.flush(); return orderedIds.stream().map(byId::get).map(ItineraryResponse::from).toList();
    }
    private void normalize(UUID dayId) { List<Itinerary> items = listEntities(dayId); for (int i = 0; i < items.size(); i++) items.get(i).changeSortOrder(10_000 + i); itineraries.flush(); for (int i = 0; i < items.size(); i++) items.get(i).changeSortOrder(i); }
    private TripDay ownedDay(UUID userId, UUID tripId, UUID dayId) { return days.findByIdAndTripIdAndTripUserId(dayId, tripId, userId).orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DAY_NOT_FOUND)); }
    private Place resolvePlace(UUID tripId, UUID placeId) { if (placeId == null) return null; return places.findByIdAndTripId(placeId, tripId).orElseThrow(() -> new BusinessException(ErrorCode.PLACE_TRIP_MISMATCH)); }
    private Itinerary find(UUID dayId, UUID id) { return itineraries.findByIdAndTripDayId(id, dayId).orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND)); }
    private List<Itinerary> listEntities(UUID dayId) { return itineraries.findAllByTripDayIdOrderBySortOrderAscIdAsc(dayId); }
    private void validateTime(LocalTime start, LocalTime end) { if ((start == null) != (end == null) || (start != null && !end.isAfter(start))) throw new BusinessException(ErrorCode.INVALID_ITINERARY_TIME); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    public record ItineraryCommand(String title, String notes, LocalTime startTime, LocalTime endTime, UUID placeId) {}
}
