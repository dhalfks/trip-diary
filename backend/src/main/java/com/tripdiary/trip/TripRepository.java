package com.tripdiary.trip;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Page<Trip> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "days")
    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
}
