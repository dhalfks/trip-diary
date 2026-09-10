package com.tripdiary.itinerary;
import java.util.*;
import com.tripdiary.trip.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TripDayRepository extends JpaRepository<TripDay, UUID> {
    Optional<TripDay> findByIdAndTripIdAndTripUserId(UUID id, UUID tripId, UUID userId);
}
