package com.tripdiary.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/** Provider-neutral object operations. Keys are opaque identifiers, not URLs. */
public interface ObjectStorageService {
    StorageType storageType();

    /**
     * Stores exactly fileSize bytes under key. The caller owns and closes content.
     * Implementations must report failures rather than silently returning success.
     */
    void upload(String key, InputStream content, String contentType, long fileSize) throws IOException;

    /** Deletes an object; an already absent object is considered successfully deleted. */
    void delete(String key) throws IOException;

    /**
     * Creates a download URL for a positive validity duration.
     * Providers must reject unsupported validity requirements explicitly.
     * URLs are generated on demand and must not be persisted as object keys.
     */
    URI createDownloadUrl(String key, Duration validity) throws IOException;
}
