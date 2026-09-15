package com.tripdiary.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "image.cleanup.enabled", havingValue = "true")
public class ImageCleanupScheduler {
    private final ImageCleanupService cleanup;
    public ImageCleanupScheduler(ImageCleanupService cleanup) { this.cleanup = cleanup; }
    @Scheduled(fixedDelayString = "${image.cleanup.fixed-delay:1h}", initialDelayString = "${image.cleanup.fixed-delay:1h}")
    public void clean() { cleanup.runOnce(); }
}
