package com.tripdiary.image;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;

public interface ImageRepository extends JpaRepository<Image, UUID> {
    @Query("select image.id from Image image where image.status = com.tripdiary.image.ImageStatus.PENDING and image.createdAt < :cutoff order by image.createdAt, image.id")
    List<UUID> findPendingCleanupIds(java.time.Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from Image image where image.id = :id")
    Optional<Image> findForCleanup(UUID id);
    List<Image> findAllByDiaryEntryTripDayTripUserId(UUID userId);
    @EntityGraph(attributePaths = "diaryEntry")
    List<Image> findAllByDiaryEntryTripDayTripIdAndStatus(UUID tripId, ImageStatus status);
    List<Image> findAllByIdInAndDiaryEntryTripDayTripIdAndStatus(Collection<UUID> ids, UUID tripId, ImageStatus status);
    List<Image> findAllByDiaryEntryIdOrderByCreatedAtAscIdAsc(UUID diaryEntryId);
    Optional<Image> findByIdAndDiaryEntryId(UUID id, UUID diaryEntryId);
    List<Image> findAllByDiaryEntryIdAndStatusOrderByCreatedAtAscIdAsc(UUID diaryEntryId, ImageStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select image from Image image where image.id = :id and image.diaryEntry.id = :diaryEntryId")
    Optional<Image> findForDeletion(UUID id, UUID diaryEntryId);
}
