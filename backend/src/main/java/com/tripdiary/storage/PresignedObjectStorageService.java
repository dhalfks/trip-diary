package com.tripdiary.storage;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Optional direct-upload capability; existing ObjectStorageService implementations remain valid. */
public interface PresignedObjectStorageService extends ObjectStorageService {
    /** Signs a create-only PUT bound to the supplied content type and byte length. */
    PresignedUpload createUploadUrl(String key, String contentType, long fileSize, Duration validity) throws IOException;

    /** Empty only when the provider confirms that the object does not exist. */
    Optional<ObjectMetadata> head(String key) throws IOException;

    record PresignedUpload(URI url, Map<String, List<String>> headers, Instant expiresAt) {}
    record ObjectMetadata(long fileSize, String contentType) {}
}
