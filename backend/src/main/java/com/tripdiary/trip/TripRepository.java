package com.tripdiary.trip;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    Page<Trip> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "days")
    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id and trip.user.id = :userId")
    Optional<Trip> findOwnedForDiaryGeneration(UUID id, UUID userId);
}
