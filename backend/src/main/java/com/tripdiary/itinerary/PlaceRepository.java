package com.tripdiary.itinerary;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlaceRepository extends JpaRepository<Place, UUID> {
    List<Place> findAllByTripIdOrderByNameAscIdAsc(UUID tripId);
    Optional<Place> findByIdAndTripId(UUID id, UUID tripId);
}
