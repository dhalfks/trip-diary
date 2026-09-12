package com.tripdiary.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.tripdiary.diary.DiaryEntry;
import com.tripdiary.diary.DiaryEntryRepository;
import com.tripdiary.storage.PresignedObjectStorageService;
import com.tripdiary.storage.StorageType;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ImageAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean ImageRepository images;
    @Autowired DiaryEntryRepository entries;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @MockitoBean PresignedObjectStorageService storage;
    User owner;
    Trip trip;
    DiaryEntry entry;
    String base;

    @BeforeEach
    void setUp() throws IOException {
        owner = users.saveAndFlush(new User("image-access-" + UUID.randomUUID() + "@example.com", "hashed-password", "owner"));
        trip = trips.saveAndFlush(new Trip(owner, "Jeju", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "Asia/Seoul"));
        entry = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Beach", "Sunny", null, null));
        base = "/api/v1/trips/" + trip.getId() + "/days/" + trip.getDays().get(0).getId() + "/entries/" + entry.getId() + "/images";
        when(storage.storageType()).thenReturn(StorageType.S3);
        when(storage.createDownloadUrl(anyString(), any())).thenReturn(URI.create("https://private.example.test/image?signature=test"));
    }

    @AfterEach
    void cleanUp() {
        trips.deleteById(trip.getId());
        users.deleteById(owner.getId());
    }

    @Test
    void listsOnlyCompletedImagesForEntryWithShortLivedUrls() throws Exception {
        Image image = saveImage(entry);
        images.saveAndFlush(Image.pending(entry, "pending.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 1234, StorageType.S3));
        DiaryEntry other = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Other", "Other", null, null));
        saveImage(other);
        String body = mvc.perform(get(base).with(as(owner.getId()))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(image.getId().toString()))
                .andExpect(jsonPath("$[0].diaryEntryId").value(entry.getId().toString()))
                .andExpect(jsonPath("$[0].downloadUrl").value("https://private.example.test/image?signature=test"))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(json.readTree(body).get(0).get("expiresAt").asText()))
                .isBetween(Instant.now().plusSeconds(290), Instant.now().plusSeconds(301));
        verify(storage).createDownloadUrl(image.getStorageKey(), Duration.ofMinutes(5));
        verify(storage, times(1)).createDownloadUrl(anyString(), any());
        assertThat(images.findById(image.getId()).orElseThrow().getStorageKey()).isEqualTo(image.getStorageKey());
    }

    @Test
    void emptyEntryReturnsEmptyListWithoutStorageCalls() throws Exception {
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(storage);
    }

    @Test
    void listSigningFailureUsesExistingStorageError() throws Exception {
        saveImage(entry);
        when(storage.createDownloadUrl(anyString(), any())).thenThrow(new IOException("unavailable"));
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IMAGE_STORAGE_UNAVAILABLE"));
    }

    @Test
    void deletesS3BeforeDatabaseAndRepeatedDeleteReturnsNotFound() throws Exception {
        Image image = saveImage(entry);
        doAnswer(invocation -> { assertThat(images.existsById(image.getId())).isTrue(); return null; })
                .when(storage).delete(image.getStorageKey());
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNoContent());
        assertThat(images.existsById(image.getId())).isFalse();
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
        verify(storage, times(1)).delete(image.getStorageKey());
    }

    @Test
    void absentObjectStillAllowsDatabaseCleanupWithoutHeadRequest() throws Exception {
        Image image = saveImage(entry);
        // ObjectStorageService.delete is idempotent; absent keys return normally.
        doNothing().when(storage).delete(image.getStorageKey());
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNoContent());
        assertThat(images.existsById(image.getId())).isFalse();
        verify(storage, never()).head(anyString());
    }

    @Test
    void s3FailurePreservesDatabaseAndRetrySucceeds() throws Exception {
        Image image = saveImage(entry);
        doThrow(new IOException("unavailable")).doNothing().when(storage).delete(image.getStorageKey());
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IMAGE_STORAGE_UNAVAILABLE"));
        assertThat(images.existsById(image.getId())).isTrue();
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNoContent());
        assertThat(images.existsById(image.getId())).isFalse();
    }

    @Test
    void databaseFailureAfterS3DeletionRollsBackAndCanBeRetried() throws Exception {
        Image image = saveImage(entry);
        // The repository is a Spring proxy; the retry's actual flush happens at transaction commit.
        doThrow(new DataIntegrityViolationException("simulated DB failure")).doNothing().when(images).flush();
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isInternalServerError());
        assertThat(images.existsById(image.getId())).isTrue();
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNoContent());
        assertThat(images.existsById(image.getId())).isFalse();
        verify(storage, times(2)).delete(image.getStorageKey());
    }

    @Test
    void unauthenticatedAndOtherUsersCannotListOrDelete() throws Exception {
        Image image = saveImage(entry);
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(delete(base + "/{id}", image.getId())).andExpect(status().isUnauthorized());
        UUID stranger = UUID.randomUUID();
        mvc.perform(get(base).with(as(stranger))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRIP_DAY_NOT_FOUND"));
        mvc.perform(delete(base + "/{id}", image.getId()).with(as(stranger))).andExpect(status().isNotFound());
        assertThat(images.existsById(image.getId())).isTrue();
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsInvalidHierarchyAndMissingEntryBeforeStorageAccess() throws Exception {
        Image image = saveImage(entry);
        for (String path : List.of(base.replace(entry.getId().toString(), UUID.randomUUID().toString()),
                base.replace(trip.getDays().get(0).getId().toString(), trip.getDays().get(1).getId().toString()),
                base.replace(trip.getId().toString(), UUID.randomUUID().toString()))) {
            mvc.perform(get(path).with(as(owner.getId()))).andExpect(status().isNotFound());
            mvc.perform(delete(path + "/{id}", image.getId()).with(as(owner.getId()))).andExpect(status().isNotFound());
        }
        verifyNoInteractions(storage);
    }

    @Test
    void missingImageOrImageFromAnotherEntryCannotBeDeleted() throws Exception {
        DiaryEntry other = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Other", "Other", null, null));
        Image image = saveImage(other);
        for (UUID id : List.of(UUID.randomUUID(), image.getId())) {
            mvc.perform(delete(base + "/{id}", id).with(as(owner.getId()))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
        }
        verifyNoInteractions(storage);
        assertThat(images.existsById(image.getId())).isTrue();
    }

    @Test
    void uploadCompleteListDeleteFlow() throws Exception {
        when(storage.createUploadUrl(anyString(), anyString(), anyLong(), any())).thenReturn(new PresignedObjectStorageService.PresignedUpload(
                URI.create("https://private.example.test/put"), Map.of(), Instant.now().plusSeconds(300)));
        String body = mvc.perform(post(base + "/upload-url").with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFileName\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1234}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("imageId").asText());
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(content().json("[]"));
        when(storage.head(anyString())).thenReturn(Optional.of(new PresignedObjectStorageService.ObjectMetadata(1234, "image/jpeg")));
        mvc.perform(post(base + "/{id}/complete", id).with(as(owner.getId()))).andExpect(status().isOk());
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete(base + "/{id}", id).with(as(owner.getId()))).andExpect(status().isNoContent());
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(content().json("[]"));
    }

    private Image saveImage(DiaryEntry target) {
        return images.saveAndFlush(new Image(target, "photo.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 1234, StorageType.S3));
    }

    private RequestPostProcessor as(UUID id) { return jwt().jwt(token -> token.subject(id.toString()).claim("token_type", "access")); }
}
