package com.tripdiary.image;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.tripdiary.diary.DiaryEntryRepository;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.itinerary.TripDayRepository;
import com.tripdiary.storage.ObjectStorageService;
import com.tripdiary.storage.S3Properties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ImageService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImageService.class);
    private final ImageRepository images;
    private final DiaryEntryRepository entries;
    private final TripDayRepository days;
    private final ObjectStorageService storage;
    private final S3Properties properties;
    private final Clock clock;

    public ImageService(ImageRepository images, DiaryEntryRepository entries, TripDayRepository days,
                        ObjectStorageService storage, S3Properties properties, Clock clock) {
        this.images = images; this.entries = entries; this.days = days;
        this.storage = storage; this.properties = properties; this.clock = clock;
    }

    public List<ImageViewResponse> list(UUID userId, UUID tripId, UUID dayId, UUID entryId) {
        ownedEntry(userId, tripId, dayId, entryId);
        return images.findAllByDiaryEntryIdAndStatusOrderByCreatedAtAscIdAsc(entryId, ImageStatus.COMPLETED)
                .stream().map(this::view).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID tripId, UUID dayId, UUID entryId, UUID imageId) {
        ownedEntry(userId, tripId, dayId, entryId);
        Image image = images.findForDeletion(imageId, entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        deleteStored(image);
    }

    // Caller holds the image row lock and a transaction. Reused by the PENDING cleanup worker.
    void deleteStored(Image image) {
        requireStorage(image);
        try {
            // S3 is not part of the DB transaction. Keep the row on S3 failure;
            // if DB commit fails after S3 deletion, retry can safely delete the absent object.
            storage.delete(image.getStorageKey());
            images.delete(image);
            images.flush();
        } catch (IOException exception) {
            log.warn("IMAGE_S3_DELETE_FAILED image={} code=IMAGE_STORAGE_UNAVAILABLE", image.getId());
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private ImageViewResponse view(Image image) {
        requireStorage(image);
        Instant expiresAt = clock.instant().plus(properties.presignTtl());
        try {
            return ImageViewResponse.from(image, storage.createDownloadUrl(image.getStorageKey(), properties.presignTtl()), expiresAt);
        } catch (IOException exception) { throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE); }
    }

    private void requireStorage(Image image) {
        if (image.getStorageType() != storage.storageType()) throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    private void ownedEntry(UUID userId, UUID tripId, UUID dayId, UUID entryId) {
        days.findByIdAndTripIdAndTripUserId(dayId, tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DAY_NOT_FOUND));
        entries.findByIdAndTripDayId(entryId, dayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_ENTRY_NOT_FOUND));
    }
}
