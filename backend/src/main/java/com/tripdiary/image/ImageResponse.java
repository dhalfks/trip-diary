package com.tripdiary.image;

import java.time.Instant;
import java.util.UUID;
import com.tripdiary.storage.StorageType;

public record ImageResponse(
        UUID id, UUID diaryEntryId, String originalFileName, String storageKey,
        String contentType, long fileSize, StorageType storageType, Instant createdAt) {
    static ImageResponse from(Image image) {
        return new ImageResponse(image.getId(), image.getDiaryEntry().getId(),
                image.getOriginalFileName(), image.getStorageKey(), image.getContentType(),
                image.getFileSize(), image.getStorageType(), image.getCreatedAt());
    }
}
