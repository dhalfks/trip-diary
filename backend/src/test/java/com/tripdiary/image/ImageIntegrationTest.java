package com.tripdiary.image;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;
import com.tripdiary.diary.DiaryEntry;
import com.tripdiary.diary.DiaryEntryRepository;
import com.tripdiary.storage.StorageType;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ImageIntegrationTest {
    @Autowired ImageRepository images;
    @Autowired DiaryEntryRepository entries;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    DiaryEntry entry;

    @BeforeEach
    void setUp() {
        User owner = users.saveAndFlush(new User("image-" + UUID.randomUUID() + "@example.com", "hashed-password", "owner"));
        Trip trip = trips.saveAndFlush(new Trip(owner, "Jeju", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), "Asia/Seoul"));
        entry = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Beach", "A sunny day", null, null));
    }

    @Test
    void persistsMetadataAndScopesQueriesToEntry() {
        Image saved = images.saveAndFlush(image("images/photo.jpg", 1234, StorageType.LOCAL));
        UUID id = saved.getId();
        UUID entryId = entry.getId();
        entityManager.clear();

        Image loaded = images.findByIdAndDiaryEntryId(id, entryId).orElseThrow();
        ImageResponse response = ImageResponse.from(loaded);
        assertThat(response).isEqualTo(new ImageResponse(id, entryId, "photo.jpg", "images/photo.jpg",
                "image/jpeg", 1234, StorageType.LOCAL, loaded.getCreatedAt()));
        assertThat(response.createdAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(images.findAllByDiaryEntryIdOrderByCreatedAtAscIdAsc(entryId)).extracting(Image::getId).containsExactly(id);
        assertThat(images.findByIdAndDiaryEntryId(id, UUID.randomUUID())).isEmpty();
        assertThat(images.findAllByDiaryEntryIdOrderByCreatedAtAscIdAsc(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletingEntryCascadesToImageMetadata() {
        UUID id = images.saveAndFlush(image("images/deleted.jpg", 1, StorageType.LOCAL)).getId();
        UUID entryId = entry.getId();
        // Start with a fresh persistence context, as in a separate diary delete request.
        entityManager.clear();
        entries.deleteById(entryId);
        entries.flush();
        entityManager.clear();
        assertThat(images.findById(id)).isEmpty();
    }

    @Test
    void rejectsDuplicateKeyWithinStorageType() {
        images.saveAndFlush(image("same-key", 1, StorageType.LOCAL));
        images.saveAndFlush(image("same-key", 1, StorageType.S3));
        assertThatThrownBy(() -> images.saveAndFlush(image("same-key", 1, StorageType.LOCAL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingDiaryEntry() {
        DiaryEntry missing = entityManager.getReference(DiaryEntry.class, UUID.randomUUID());
        Image image = new Image(missing, "photo.jpg", "missing-entry", "image/jpeg", 1, StorageType.LOCAL);
        assertThatThrownBy(() -> images.saveAndFlush(image))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeFileSize() {
        assertThatThrownBy(() -> images.saveAndFlush(image("negative", -1, StorageType.LOCAL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Image image(String key, long size, StorageType type) {
        return new Image(entry, "photo.jpg", key, "image/jpeg", size, type);
    }
}
