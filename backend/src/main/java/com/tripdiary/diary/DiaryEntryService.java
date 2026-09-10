package com.tripdiary.diary;

import java.util.*;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.itinerary.*;
import com.tripdiary.trip.TripDay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiaryEntryService {
    private final DiaryEntryRepository entries;
    private final TripDayRepository days;
    private final ItineraryRepository itineraries;
    private final PlaceRepository places;

    public DiaryEntryService(DiaryEntryRepository entries, TripDayRepository days, ItineraryRepository itineraries, PlaceRepository places) {
        this.entries = entries; this.days = days; this.itineraries = itineraries; this.places = places;
    }

    public List<DiaryEntryResponse> list(UUID userId, UUID tripId, UUID dayId) {
        ownedDay(userId, tripId, dayId);
        return entries.findAllByTripDayIdOrderByUpdatedAtDescIdAsc(dayId).stream().map(DiaryEntryResponse::from).toList();
    }

    @Transactional
    public DiaryEntryResponse create(UUID userId, UUID tripId, UUID dayId, DiaryEntryCommand command) {
        TripDay day = ownedDay(userId, tripId, dayId);
        Links links = resolveLinks(tripId, dayId, command.itineraryId(), command.placeId());
        return DiaryEntryResponse.from(entries.save(new DiaryEntry(day, clean(command.title()), clean(command.content()), links.itinerary(), links.place())));
    }

    @Transactional
    public DiaryEntryResponse update(UUID userId, UUID tripId, UUID dayId, UUID entryId, DiaryEntryCommand command) {
        ownedDay(userId, tripId, dayId);
        DiaryEntry entry = find(dayId, entryId);
        Links links = resolveLinks(tripId, dayId, command.itineraryId(), command.placeId());
        entry.change(clean(command.title()), clean(command.content()), links.itinerary(), links.place());
        return DiaryEntryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID userId, UUID tripId, UUID dayId, UUID entryId) {
        ownedDay(userId, tripId, dayId);
        entries.delete(find(dayId, entryId));
    }

    private TripDay ownedDay(UUID userId, UUID tripId, UUID dayId) {
        return days.findByIdAndTripIdAndTripUserId(dayId, tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DAY_NOT_FOUND));
    }

    private DiaryEntry find(UUID dayId, UUID entryId) {
        return entries.findByIdAndTripDayId(entryId, dayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_ENTRY_NOT_FOUND));
    }

    private Links resolveLinks(UUID tripId, UUID dayId, UUID itineraryId, UUID placeId) {
        Itinerary itinerary = null;
        Place place = null;
        if (itineraryId != null) {
            itinerary = itineraries.findByIdAndTripDayId(itineraryId, dayId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_ENTRY_LINK_MISMATCH));
        }
        if (placeId != null) {
            place = places.findByIdAndTripId(placeId, tripId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_ENTRY_LINK_MISMATCH));
        }
        return new Links(itinerary, place);
    }

    private String clean(String value) { return value.strip(); }
    public record DiaryEntryCommand(String title, String content, UUID itineraryId, UUID placeId) {}
    private record Links(Itinerary itinerary, Place place) {}
}
