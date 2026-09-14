package com.tripdiary.diary;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, UUID> {
    @Query("select entry from DiaryEntry entry join fetch entry.tripDay day where day.trip.id = :tripId order by day.date, entry.createdAt, entry.id")
    List<DiaryEntry> findAllForDiaryGeneration(UUID tripId);
    List<DiaryEntry> findAllByTripDayIdOrderByUpdatedAtDescIdAsc(UUID tripDayId);
    Optional<DiaryEntry> findByIdAndTripDayId(UUID id, UUID tripDayId);
}
