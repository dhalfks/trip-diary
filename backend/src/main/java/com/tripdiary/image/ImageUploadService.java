package com.tripdiary.image;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.tripdiary.diary.DiaryEntry;
import com.tripdiary.diary.DiaryEntryRepository;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.itinerary.TripDayRepository;
import com.tripdiary.storage.PresignedObjectStorageService;
import com.tripdiary.storage.S3Properties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ImageUploadService {
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Map<String, Set<String>> EXTENSIONS = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"), "image/png", Set.of("png"),
            "image/webp", Set.of("webp"), "image/heic", Set.of("heic"), "image/heif", Set.of("heif"));
    private final ImageRepository images;
    private final DiaryEntryRepository entries;
    private final TripDayRepository days;
    private final PresignedObjectStorageService storage;
    private final S3Properties properties;

    public ImageUploadService(ImageRepository images, DiaryEntryRepository entries, TripDayRepository days,
                              PresignedObjectStorageService storage, S3Properties properties) {
        this.images = images; this.entries = entries; this.days = days;
        this.storage = storage; this.properties = properties;
    }

    @Transactional
    public ImageUploadResponse initiate(UUID userId, UUID tripId, UUID dayId, UUID entryId, ImageUploadCommand command) {
        DiaryEntry entry = ownedEntry(userId, tripId, dayId, entryId);
        validate(command);
        String key = "images/" + UUID.randomUUID();
        Image image = images.saveAndFlush(Image.pending(entry, command.originalFileName(), key,
                command.contentType(), command.fileSize(), storage.storageType()));
        try {
            var upload = storage.createUploadUrl(key, image.getContentType(), image.getFileSize(), properties.presignTtl());
            return new ImageUploadResponse(image.getId(), image.getStatus(), "PUT", upload.url(), upload.headers(), upload.expiresAt());
        } catch (IOException exception) { throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE); }
    }

    @Transactional
    public ImageUploadCompletionResponse complete(UUID userId, UUID tripId, UUID dayId, UUID entryId, UUID imageId) {
        ownedEntry(userId, tripId, dayId, entryId);
        Image image = images.findForDeletion(imageId, entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        if (image.getStatus() == ImageStatus.COMPLETED) return new ImageUploadCompletionResponse(image.getId(), image.getStatus());
        if (image.getStorageType() != storage.storageType()) throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        try {
            var object = storage.head(image.getStorageKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_UPLOAD_NOT_READY));
            if (object.fileSize() != image.getFileSize() || !image.getContentType().equals(object.contentType())) {
                throw new BusinessException(ErrorCode.IMAGE_UPLOAD_MISMATCH);
            }
            image.completeUpload();
            return new ImageUploadCompletionResponse(image.getId(), image.getStatus());
        } catch (IOException exception) { throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE); }
    }

    private DiaryEntry ownedEntry(UUID userId, UUID tripId, UUID dayId, UUID entryId) {
        days.findByIdAndTripIdAndTripUserId(dayId, tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_DAY_NOT_FOUND));
        return entries.findByIdAndTripDayId(entryId, dayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIARY_ENTRY_NOT_FOUND));
    }

    private void validate(ImageUploadCommand command) {
        String name = command.originalFileName();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl) || command.contentType() == null
                || command.fileSize() < 1 || command.fileSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (name.lastIndexOf('.') < 1 || !EXTENSIONS.getOrDefault(command.contentType(), Set.of()).contains(extension)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public record ImageUploadCommand(String originalFileName, String contentType, long fileSize) {}
    public record ImageUploadCompletionResponse(UUID imageId, ImageStatus status) {}
}
