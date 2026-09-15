package com.tripdiary.image;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("image.cleanup")
public record ImageCleanupProperties(@DefaultValue("false") boolean enabled, @DefaultValue("24h") Duration pendingAge,
                                     @DefaultValue("1h") Duration fixedDelay, @DefaultValue("100") int batchSize) {
    public ImageCleanupProperties {
        // Always outlive the maximum presigned TTL (15 minutes), with a generous transfer grace period.
        if (pendingAge == null || pendingAge.compareTo(Duration.ofHours(1)) < 0) throw new IllegalArgumentException("cleanup.pending-age must be at least 1h");
        if (fixedDelay == null || fixedDelay.compareTo(Duration.ofMinutes(1)) < 0) throw new IllegalArgumentException("cleanup.fixed-delay must be at least 1m");
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("cleanup.batch-size must be 1..1000");
    }
}
