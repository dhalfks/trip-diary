package com.tripdiary.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripdiary.diary.*;
import com.tripdiary.storage.*;
import com.tripdiary.trip.*;
import com.tripdiary.user.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"image.cleanup.enabled=true", "image.cleanup.pending-age=2h", "image.cleanup.fixed-delay=1d", "image.cleanup.batch-size=2"})
@ActiveProfiles("test")
class ImageCleanupIntegrationTest {
    @Autowired UserRepository users; @Autowired TripRepository trips; @Autowired DiaryEntryRepository entries;
    @Autowired ImageRepository images;
    @Autowired ImageCleanupService cleanup; @Autowired JdbcTemplate jdbc;
    @Autowired ImageService deletion; @Autowired ImageUploadService uploads;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PresignedObjectStorageService storage;
    User user; Trip trip; DiaryEntry entry;
    ListAppender<ILoggingEvent> logs; Logger logger;
    @BeforeEach void setup() {
        when(storage.storageType()).thenReturn(StorageType.S3);
        user = users.saveAndFlush(new User("cleanup-" + UUID.randomUUID() + "@example.test", "test-hash", "tester"));
        trip = trips.saveAndFlush(new Trip(user, "Trip", LocalDate.now(), LocalDate.now(), "Asia/Seoul"));
        entry = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Entry", "Text", null, null));
        logger = (Logger) LoggerFactory.getLogger(ImageCleanupService.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void cleanupData() { logger.detachAppender(logs); logs.stop(); users.deleteById(user.getId()); }
    @Test void deletesOnlyOldPendingIncludingMissingObjectsAndUncompletedPuts() throws Exception {
        Image missing = image(true, 4), uploaded = image(true, 3), recent = image(true, 0), completed = image(false, 4);
        doAnswer(call -> {
            String key = call.getArgument(0);
            assertThat(images.findAll().stream().anyMatch(i -> i.getStorageKey().equals(key))).isTrue();
            return null;
        }).when(storage).delete(anyString());
        var result = cleanup.runOnce();
        assertThat(result.deleted()).isEqualTo(2);
        assertThat(images.existsById(missing.getId())).isFalse(); assertThat(images.existsById(uploaded.getId())).isFalse();
        assertThat(images.existsById(recent.getId())).isTrue(); assertThat(images.existsById(completed.getId())).isTrue();
        verify(storage).delete(missing.getStorageKey()); verify(storage).delete(uploaded.getStorageKey());
        verify(storage, never()).delete(recent.getStorageKey()); verify(storage, never()).delete(completed.getStorageKey());
        assertThat(cleanup.runOnce().candidates()).isZero(); verify(storage, times(2)).delete(anyString());
    }
    @Test void s3FailureRetainsMetadataAndCanBeRetriedWithoutLeakingSecrets() throws Exception {
        Image image = image(true, 4);
        doThrow(new IOException("password JWT AWS_SECRET https://private.test/photo?X-Amz-Signature=secret")).when(storage).delete(image.getStorageKey());
        assertThat(cleanup.runOnce().failed()).isEqualTo(1); assertThat(images.existsById(image.getId())).isTrue();
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList().toString()).contains("IMAGE_CLEANUP_FAILED", image.getId().toString(), "IMAGE_STORAGE_UNAVAILABLE")
                .doesNotContain("AWS_SECRET", "X-Amz", "password", image.getStorageKey());
        doNothing().when(storage).delete(image.getStorageKey());
        assertThat(cleanup.runOnce().deleted()).isEqualTo(1); assertThat(images.existsById(image.getId())).isFalse();
    }
    @Test void changedStatusBetweenSelectionAndLockIsRechecked() {
        Image image = image(true, 4);
        jdbc.update("update images set status = 'COMPLETED' where id = ?", image.getId());
        assertThat(cleanup.cleanupCandidate(image.getId(), Instant.now().minus(Duration.ofHours(2)))).isFalse();
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageStatus.COMPLETED);
        verifyNoInteractions(storage);
    }
    @Test void enforcesBatchSizeAndNeverInfersOtherKeysFromUserOrPrefix() throws Exception {
        Image a = image(true, 5), b = image(true, 4), c = image(true, 3);
        assertThat(cleanup.runOnce().deleted()).isEqualTo(2);
        assertThat(images.existsById(c.getId())).isTrue();
        assertThat(cleanup.runOnce().deleted()).isEqualTo(1);
        verify(storage).delete(a.getStorageKey()); verify(storage).delete(b.getStorageKey()); verify(storage).delete(c.getStorageKey());
        verify(storage, times(3)).delete(anyString());
    }
    @Test void disabledCleanupDoesNotQueryOrDeleteAndUnsafeConfigurationIsRejected() {
        image(true, 4); clearInvocations(storage);
        var disabled = new ImageCleanupService(images, deletion, new ImageCleanupProperties(false, Duration.ofHours(24), Duration.ofHours(1), 100), Clock.systemUTC(), transactions);
        assertThat(disabled.runOnce().candidates()).isZero(); verifyNoInteractions(storage);
        assertThatThrownBy(() -> new ImageCleanupProperties(true, Duration.ofMinutes(15), Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageCleanupProperties(true, Duration.ofHours(24), Duration.ofSeconds(1), 100)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void databaseFailureRetainsPendingForRetryAfterS3WasDeleted() throws Exception {
        Image image = image(true, 4);
        jdbc.execute("create table cleanup_test_guard (image_id uuid references images(id))");
        try {
            jdbc.update("insert into cleanup_test_guard values (?)", image.getId());
            assertThat(cleanup.runOnce().failed()).isEqualTo(1); assertThat(images.existsById(image.getId())).isTrue();
            verify(storage).delete(image.getStorageKey());
        } finally { jdbc.execute("drop table cleanup_test_guard"); }
        assertThat(cleanup.runOnce().deleted()).isEqualTo(1); verify(storage, times(2)).delete(image.getStorageKey());
    }
    @Test void completeAndCleanupSerializeOnTheSameImageLock() throws Exception {
        Image image = image(true, 4);
        var headEntered = new java.util.concurrent.CountDownLatch(1);
        var finishComplete = new java.util.concurrent.CountDownLatch(1);
        when(storage.head(image.getStorageKey())).thenAnswer(call -> {
            headEntered.countDown();
            if (!finishComplete.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("timeout");
            return Optional.of(new PresignedObjectStorageService.ObjectMetadata(100, "image/jpeg"));
        });
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var complete = pool.submit(() -> uploads.complete(user.getId(), trip.getId(), trip.getDays().get(0).getId(), entry.getId(), image.getId()));
            assertThat(headEntered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var cleaning = pool.submit(() -> cleanup.runOnce());
            finishComplete.countDown();
            assertThat(complete.get(15, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo(ImageStatus.COMPLETED);
            assertThat(cleaning.get(15, java.util.concurrent.TimeUnit.SECONDS).deleted()).isZero();
            verify(storage, never()).delete(anyString());
            assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(ImageStatus.COMPLETED);
        } finally { finishComplete.countDown(); pool.shutdownNow(); }
    }
    private Image image(boolean pending, int hoursOld) {
        Image image = pending ? Image.pending(entry, "private.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 100, StorageType.S3)
                : new Image(entry, "private.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 100, StorageType.S3);
        images.saveAndFlush(image);
        jdbc.update("update images set created_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(hoursOld))), image.getId());
        return image;
    }
}
