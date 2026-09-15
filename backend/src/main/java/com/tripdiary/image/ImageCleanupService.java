package com.tripdiary.image;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.tripdiary.global.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
    private final ImageRepository images;
    private final ImageService deletion;
    private final ImageCleanupProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final AtomicBoolean running = new AtomicBoolean();
    public ImageCleanupService(ImageRepository images, ImageService deletion, ImageCleanupProperties properties,
                               Clock clock, PlatformTransactionManager manager) {
        this.images = images; this.deletion = deletion; this.properties = properties; this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result runOnce() {
        if (!properties.enabled() || !running.compareAndSet(false, true)) return new Result(0, 0, 0, 0);
        UUID run = UUID.randomUUID();
        int deleted = 0, skipped = 0, failed = 0;
        try {
            var cutoff = clock.instant().minus(properties.pendingAge());
            var candidates = images.findPendingCleanupIds(cutoff, PageRequest.of(0, properties.batchSize()));
            for (UUID id : candidates) {
                try {
                    boolean removed = cleanupCandidate(id, cutoff);
                    if (removed) deleted++; else skipped++;
                } catch (RuntimeException exception) {
                    failed++;
                    String code = exception instanceof BusinessException business ? business.getErrorCode().name() : "INTERNAL_SERVER_ERROR";
                    log.warn("IMAGE_CLEANUP_FAILED run={} image={} code={}", run, id, code);
                }
            }
            var result = new Result(candidates.size(), deleted, skipped, failed);
            log.info("IMAGE_CLEANUP_FINISHED run={} candidates={} deleted={} skipped={} failed={}", run, result.candidates(), deleted, skipped, failed);
            return result;
        } catch (RuntimeException exception) {
            // Do not let scheduler error handling print exception messages/SQL values.
            log.error("IMAGE_CLEANUP_RUN_FAILED run={} type={}", run, exception.getClass().getSimpleName());
            return new Result(0, deleted, skipped, failed + 1);
        } finally { running.set(false); }
    }
    boolean cleanupCandidate(UUID id, java.time.Instant cutoff) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var image = images.findForCleanup(id).orElse(null);
            if (image == null || image.getStatus() != ImageStatus.PENDING || !image.getCreatedAt().isBefore(cutoff)) return false;
            // Same S3-first policy as the authenticated DELETE API, one transaction per image.
            deletion.deleteStored(image);
            return true;
        }));
    }
    public record Result(int candidates, int deleted, int skipped, int failed) {}
}
