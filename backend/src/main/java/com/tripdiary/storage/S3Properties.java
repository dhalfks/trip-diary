package com.tripdiary.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(String region, String bucket, Duration presignTtl) {
    public S3Properties {
        if (region == null || region.isBlank()) throw new IllegalStateException("S3 region is required");
        requireShortValidity(presignTtl);
    }

    static void requireShortValidity(Duration validity) {
        if (validity == null || validity.compareTo(Duration.ofSeconds(1)) < 0
                || validity.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("S3 URL validity must be between 1 second and 15 minutes");
        }
    }
}
