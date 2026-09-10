package com.tripdiary.diary;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, UUID> {
    List<DiaryEntry> findAllByTripDayIdOrderByUpdatedAtDescIdAsc(UUID tripDayId);
    Optional<DiaryEntry> findByIdAndTripDayId(UUID id, UUID tripDayId);
}
