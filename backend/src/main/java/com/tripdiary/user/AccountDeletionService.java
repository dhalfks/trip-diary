package com.tripdiary.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;
import com.tripdiary.image.ImageRepository;
import com.tripdiary.storage.ObjectStorageService;
import com.tripdiary.storage.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountDeletionService {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private final UserRepository users;
    private final ImageRepository images;
    private final ObjectStorageService storage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AccountDeletionService(UserRepository users, ImageRepository images, ObjectStorageService storage,
                                  JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.users = users; this.images = images; this.storage = storage; this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // No transaction spans an S3 request. The database commit immediately invalidates authentication.
    public void delete(UUID userId) {
        UUID operation = UUID.randomUUID();
        List<StoredImage> targets = Objects.requireNonNull(transaction.execute(status -> {
            User user = users.findForDeletion(userId).filter(User::isActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            // Parent locks prevent FK inserts from adding images after the snapshot is collected.
            lockImageParents(userId);
            List<StoredImage> owned = images.findAllByDiaryEntryTripDayTripUserId(userId).stream()
                    .map(image -> new StoredImage(image.getId(), image.getStorageType(), image.getStorageKey())).toList();
            log.info("ACCOUNT_DELETION_PREPARED operation={} user={} images={}", operation, userId, owned.size());
            for (StoredImage image : owned) {
                // Recoverable manifest, including PENDING uploads; no filenames, credentials or signed URLs.
                log.info("ACCOUNT_DELETION_OBJECT operation={} image={} storage={} keyBase64={}",
                        operation, image.id(), image.type(), encodedKey(image.key()));
            }
            users.delete(user);
            users.flush();
            return owned;
        }));
        log.info("ACCOUNT_DELETION_DB_COMMITTED operation={}", operation);
        int failures = 0;
        for (StoredImage image : targets) {
            try {
                if (image.type() != storage.storageType()) throw new IOException("Storage mismatch");
                storage.delete(image.key());
                log.info("ACCOUNT_DELETION_OBJECT_DELETED operation={} image={}", operation, image.id());
            } catch (IOException | RuntimeException exception) {
                failures++;
                // SDK/exception messages can contain secrets. Log identifiers only for operator retries.
                log.error("ACCOUNT_DELETION_S3_RETRY operation={} image={} storage={} keyBase64={}",
                        operation, image.id(), image.type(), encodedKey(image.key()));
            }
        }
        log.info("ACCOUNT_DELETION_FINISHED operation={} objects={} retryRequired={}", operation, targets.size(), failures);
    }

    private void lockImageParents(UUID userId) {
        jdbc.queryForList("select id from trips where user_id = ? for update", UUID.class, userId);
        jdbc.queryForList("select id from trip_days where trip_id in (select id from trips where user_id = ?) for update", UUID.class, userId);
        jdbc.queryForList("select id from diary_entries where trip_day_id in (select d.id from trip_days d join trips t on t.id = d.trip_id where t.user_id = ?) for update", UUID.class, userId);
    }

    private static String encodedKey(String key) {
        return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private record StoredImage(UUID id, StorageType type, String key) {}
}
