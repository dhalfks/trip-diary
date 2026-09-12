package com.tripdiary.image;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Download URLs are transient response data, never persisted in Image. */
public record ImageViewResponse(UUID id, UUID diaryEntryId, String originalFileName,
                                String contentType, long fileSize, Instant createdAt,
                                URI downloadUrl, Instant expiresAt) {
    static ImageViewResponse from(Image image, URI downloadUrl, Instant expiresAt) {
        return new ImageViewResponse(image.getId(), image.getDiaryEntry().getId(), image.getOriginalFileName(),
                image.getContentType(), image.getFileSize(), image.getCreatedAt(), downloadUrl, expiresAt);
    }
}
