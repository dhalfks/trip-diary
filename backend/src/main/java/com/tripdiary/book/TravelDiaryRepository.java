package com.tripdiary.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelDiaryRepository extends JpaRepository<TravelDiary, UUID> {
    long countByTripId(UUID tripId);
    @EntityGraph(attributePaths = "pages")
    List<TravelDiary> findAllByTripIdOrderByCreatedAtDescIdDesc(UUID tripId);
    @EntityGraph(attributePaths = "pages")
    Optional<TravelDiary> findByIdAndTripId(UUID id, UUID tripId);
}
