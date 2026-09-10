package com.tripdiary.itinerary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
    List<Itinerary> findAllByTripDayIdOrderBySortOrderAscIdAsc(UUID tripDayId);
    Optional<Itinerary> findByIdAndTripDayId(UUID id, UUID tripDayId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Itinerary i set i.place = null where i.place.id = :placeId")
    int clearPlace(UUID placeId);
}
