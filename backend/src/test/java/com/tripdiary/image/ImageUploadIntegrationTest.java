package com.tripdiary.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.tripdiary.storage.PresignedObjectStorageService.ObjectMetadata;
import com.tripdiary.storage.PresignedObjectStorageService.PresignedUpload;
import com.tripdiary.storage.StorageType;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ImageUploadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ImageRepository images;
    @Autowired DiaryEntryRepository entries;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @MockitoBean PresignedObjectStorageService storage;
    User owner;
    Trip trip;
    DiaryEntry entry;
    String base;
    static final String REQUEST = "{\"originalFileName\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1234}";

    @BeforeEach
    void setUp() throws IOException {
        owner = users.saveAndFlush(new User("upload-" + UUID.randomUUID() + "@example.com", "hashed-password", "owner"));
        trip = trips.saveAndFlush(new Trip(owner, "Jeju", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "Asia/Seoul"));
        entry = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Beach", "Sunny", null, null));
        base = "/api/v1/trips/" + trip.getId() + "/days/" + trip.getDays().get(0).getId() + "/entries/" + entry.getId() + "/images";
        when(storage.storageType()).thenReturn(StorageType.S3);
        when(storage.createUploadUrl(anyString(), anyString(), anyLong(), any())).thenReturn(new PresignedUpload(
                URI.create("https://private.example.test/upload"), Map.of("content-type", List.of("image/jpeg")), Instant.now().plusSeconds(300)));
    }

    @AfterEach
    void cleanUp() {
        trips.deleteById(trip.getId());
        users.deleteById(owner.getId());
    }

    @Test
    void createsPendingUploadThenVerifiesAndCompletesIdempotently() throws Exception {
        UUID id = initiate();
        Image image = images.findById(id).orElseThrow();
        assertThat(image.getStatus()).isEqualTo(ImageStatus.PENDING);
        assertThat(image.getStorageType()).isEqualTo(StorageType.S3);
        assertThat(image.getStorageKey()).matches("images/[0-9a-f-]{36}");
        assertThat(UUID.fromString(image.getStorageKey().substring(7)).version()).isEqualTo(4);
        verify(storage).createUploadUrl(image.getStorageKey(), "image/jpeg", 1234, Duration.ofMinutes(5));
        verify(storage, never()).upload(anyString(), any(), anyString(), anyLong());
        when(storage.head(image.getStorageKey())).thenReturn(Optional.of(new ObjectMetadata(1234, "image/jpeg")));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(base + "/{id}/complete", id).with(as(owner.getId())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.imageId").value(id.toString()))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
        assertThat(images.findById(id).orElseThrow().getStatus()).isEqualTo(ImageStatus.COMPLETED);
        verify(storage, times(1)).head(image.getStorageKey());
    }

    @Test
    void requiresAuthenticationAndOwnershipOnBothEndpoints() throws Exception {
        mvc.perform(post(base + "/upload-url").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/upload-url").with(as(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound());
        verify(storage, never()).createUploadUrl(anyString(), anyString(), anyLong(), any());
        UUID id = initiate();
        mvc.perform(post(base + "/{id}/complete", id)).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/{id}/complete", id).with(as(UUID.randomUUID()))).andExpect(status().isNotFound());
        verify(storage, never()).head(anyString());
    }

    @Test
    void rejectsMissingEntryAndWrongHierarchy() throws Exception {
        for (String path : List.of(base.replace(entry.getId().toString(), UUID.randomUUID().toString()),
                base.replace(trip.getDays().get(0).getId().toString(), trip.getDays().get(1).getId().toString()),
                base.replace(trip.getId().toString(), UUID.randomUUID().toString()))) {
            mvc.perform(post(path + "/upload-url").with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isNotFound());
        }
        verify(storage, never()).createUploadUrl(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void completionCannotUseImageFromAnotherEntry() throws Exception {
        UUID id = initiate();
        DiaryEntry other = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Other", "Other", null, null));
        String otherBase = base.replace(entry.getId().toString(), other.getId().toString());
        mvc.perform(post(otherBase + "/{id}/complete", id).with(as(owner.getId()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/{id}/complete", UUID.randomUUID()).with(as(owner.getId()))).andExpect(status().isNotFound());
        verify(storage, never()).head(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"originalFileName\":\"x.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":0}",
            "{\"originalFileName\":\"x.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":10485761}",
            "{\"originalFileName\":\"x.jpg\",\"contentType\":\"image/jpeg\"}",
            "{\"originalFileName\":\"x.svg\",\"contentType\":\"image/svg+xml\",\"fileSize\":1}",
            "{\"originalFileName\":\"x.png\",\"contentType\":\"image/jpeg\",\"fileSize\":1}",
            "{\"originalFileName\":\"../x.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1}",
            "{\"originalFileName\":\"x\\n.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1}",
            "{\"originalFileName\":\" \",\"contentType\":\"image/jpeg\",\"fileSize\":1}"
    })
    void rejectsInvalidFilesBeforeSigning(String request) throws Exception {
        mvc.perform(post(base + "/upload-url").with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(images.findAllByDiaryEntryIdOrderByCreatedAtAscIdAsc(entry.getId())).isEmpty();
        verify(storage, never()).createUploadUrl(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void leavesPendingWhenObjectIsMissingOrMetadataDoesNotMatch() throws Exception {
        UUID id = initiate();
        when(storage.head(anyString())).thenReturn(Optional.empty(),
                Optional.of(new ObjectMetadata(1, "image/jpeg")), Optional.of(new ObjectMetadata(1234, "image/png")));
        for (String code : List.of("IMAGE_UPLOAD_NOT_READY", "IMAGE_UPLOAD_MISMATCH", "IMAGE_UPLOAD_MISMATCH")) {
            mvc.perform(post(base + "/{id}/complete", id).with(as(owner.getId())))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));
            assertThat(images.findById(id).orElseThrow().getStatus()).isEqualTo(ImageStatus.PENDING);
        }
    }

    @Test
    void rollsBackRegistrationOnSigningFailure() throws Exception {
        when(storage.createUploadUrl(anyString(), anyString(), anyLong(), any())).thenThrow(new IOException("unavailable"));
        mvc.perform(post(base + "/upload-url").with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("IMAGE_STORAGE_UNAVAILABLE"));
        assertThat(images.findAllByDiaryEntryIdOrderByCreatedAtAscIdAsc(entry.getId())).isEmpty();
    }

    @Test
    void leavesPendingOnStorageFailureAndAllowsRetry() throws Exception {
        UUID id = initiate();
        when(storage.head(anyString())).thenThrow(new IOException("unavailable"))
                .thenReturn(Optional.of(new ObjectMetadata(1234, "image/jpeg")));
        mvc.perform(post(base + "/{id}/complete", id).with(as(owner.getId()))).andExpect(status().isServiceUnavailable());
        assertThat(images.findById(id).orElseThrow().getStatus()).isEqualTo(ImageStatus.PENDING);
        mvc.perform(post(base + "/{id}/complete", id).with(as(owner.getId()))).andExpect(status().isOk());
    }

    private UUID initiate() throws Exception {
        String body = mvc.perform(post(base + "/upload-url").with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty()).andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("imageId").asText());
    }

    private RequestPostProcessor as(UUID id) { return jwt().jwt(token -> token.subject(id.toString()).claim("token_type", "access")); }
}
