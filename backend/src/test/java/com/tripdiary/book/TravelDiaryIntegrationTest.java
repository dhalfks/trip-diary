package com.tripdiary.book;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.tripdiary.diary.*;
import com.tripdiary.image.*;
import com.tripdiary.storage.PresignedObjectStorageService;
import com.tripdiary.storage.StorageType;
import com.tripdiary.trip.*;
import com.tripdiary.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class TravelDiaryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired TripRepository trips; @Autowired UserRepository users; @Autowired DiaryEntryRepository entries;
    @Autowired ImageRepository images; @Autowired TravelDiaryRepository diaries;
    @MockitoBean PresignedObjectStorageService storage;
    User owner; Trip trip; String base;

    @BeforeEach
    void setUp() throws IOException {
        owner = users.saveAndFlush(new User("book-" + UUID.randomUUID() + "@example.com", "hashed-password", "owner"));
        trip = trips.saveAndFlush(new Trip(owner, "Jeju", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), "Asia/Seoul"));
        base = "/api/v1/trips/" + trip.getId() + "/diaries";
        when(storage.storageType()).thenReturn(StorageType.S3);
        when(storage.createDownloadUrl(anyString(), any())).thenReturn(URI.create("https://private.example.test/photo?signature=test"));
    }

    @AfterEach
    void cleanUp() { trips.deleteById(trip.getId()); users.deleteById(owner.getId()); }

    @Test
    void createsPersistsJsonAndPreviewsBothTemplatesWithCompletedImagesOnly() throws Exception {
        DiaryEntry entry = entry(0, "First memory");
        Image image = image(entry);
        images.saveAndFlush(Image.pending(entry, "pending.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 1, StorageType.S3));
        for (String template : new String[]{"CLASSIC", "PHOTO"}) {
            JsonNode created = create(template, null);
            assertThat(created.get("coverImageId").asText()).isEqualTo(image.getId().toString());
            assertThat(created.get("status").asText()).isEqualTo("READY");
            JsonNode detail = detail(created.get("id").asText());
            assertThat(detail.get("pages").get(0).get("pageOrder").asInt()).isEqualTo(1);
            assertThat(detail.get("pages").get(0).get("pageType").asText()).isEqualTo("COVER");
            assertThat(detail.get("pages").get(1).get("content").get("text").asText()).isEqualTo("First memory");
            assertThat(detail.get("pages").get(1).get("content").get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(detail.get("pages").get(1).get("layoutType").asText()).isEqualTo(template.equals("PHOTO") ? "PHOTO_TEXT" : "TEXT");
            assertThat(detail.get("images").size()).isEqualTo(1);
            assertThat(detail.get("images").get(0).get("downloadUrl").asText()).contains("signature=");
            for (int i = 0; i < detail.get("pages").size(); i++) assertThat(detail.get("pages").get(i).get("pageOrder").asInt()).isEqualTo(i + 1);
        }
        mvc.perform(get(base).with(as(owner.getId()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        verify(storage, times(2)).createDownloadUrl(image.getStorageKey(), Duration.ofMinutes(5));
    }

    @Test
    void emptyTripAndTextOnlyTripGenerateWithoutStorage() throws Exception {
        JsonNode empty = create("PHOTO", null);
        assertThat(empty.get("pageCount").asInt()).isEqualTo(1);
        assertThat(empty.get("coverImageId").isNull()).isTrue();
        assertThat(detail(empty.get("id").asText()).get("images").isEmpty()).isTrue();
        entry(1, "Only words");
        JsonNode text = create("CLASSIC", null);
        assertThat(text.get("pageCount").asInt()).isEqualTo(2);
        verifyNoInteractions(storage);
    }

    @Test
    void explicitCoverWinsAndPendingForeignOrMissingImagesAreRejected() throws Exception {
        DiaryEntry entry = entry(0, "Memory"); image(entry); Image selected = image(entry);
        assertThat(create("CLASSIC", selected.getId()).get("coverImageId").asText()).isEqualTo(selected.getId().toString());
        Image pending = images.saveAndFlush(Image.pending(entry, "pending.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 1, StorageType.S3));
        Trip other = trips.saveAndFlush(new Trip(owner, "Other", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1), "Asia/Seoul"));
        try {
            Image foreign = image(entries.saveAndFlush(new DiaryEntry(other.getDays().get(0), "Other", "Other", null, null)));
            for (UUID id : new UUID[]{pending.getId(), foreign.getId(), UUID.randomUUID()}) {
                mvc.perform(post(base).with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateType\":\"PHOTO\",\"coverImageId\":\"" + id + "\"}"))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DIARY_COVER_INVALID"));
            }
        } finally { trips.deleteById(other.getId()); }
        assertThat(diaries.countByTripId(trip.getId())).isEqualTo(1);
    }

    @Test
    void regenerationPreservesSnapshotsAndEnforcesFiveResultLimit() throws Exception {
        DiaryEntry entry = entry(0, "Original text");
        JsonNode first = create("CLASSIC", null);
        entry.change("Changed", "New text", null, null); entries.saveAndFlush(entry);
        JsonNode second = create("CLASSIC", null);
        assertThat(first.get("id").asText()).isNotEqualTo(second.get("id").asText());
        assertThat(detail(first.get("id").asText()).get("pages").get(1).get("content").get("text").asText()).isEqualTo("Original text");
        assertThat(detail(second.get("id").asText()).get("pages").get(1).get("content").get("text").asText()).isEqualTo("New text");
        for (int i = 0; i < 3; i++) create("PHOTO", null);
        mvc.perform(post(base).with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content("{\"templateType\":\"CLASSIC\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DIARY_LIMIT_REACHED"));
        mvc.perform(delete(base + "/" + first.get("id").asText()).with(as(owner.getId()))).andExpect(status().isNoContent());
        create("CLASSIC", null);
        assertThat(diaries.countByTripId(trip.getId())).isEqualTo(5);
    }

    @Test
    void concurrentGenerationCannotExceedFiveResults() throws Exception {
        for (int i = 0; i < 4; i++) create("CLASSIC", null);
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> generate = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            return mvc.perform(post(base).with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateType\":\"CLASSIC\"}")).andReturn().getResponse().getStatus();
        };
        try {
            var first = executor.submit(generate);
            var second = executor.submit(generate);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(diaries.countByTripId(trip.getId())).isEqualTo(5);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void deletedSourcePhotosBecomePlaceholdersAndSourceTextRemains() throws Exception {
        DiaryEntry entry = entry(0, "Keep this memory"); Image image = image(entry);
        String id = create("PHOTO", null).get("id").asText();
        images.deleteById(image.getId());
        JsonNode preview = detail(id);
        assertThat(preview.get("diary").get("coverImageId").isNull()).isTrue();
        assertThat(preview.get("pages").get(1).get("content").get("text").asText()).isEqualTo("Keep this memory");
        assertThat(preview.get("images").isEmpty()).isTrue();
        verifyNoInteractions(storage);
    }

    @Test
    void storageFailureDoesNotPreventGenerationOrTextPreview() throws Exception {
        image(entry(0, "Still readable"));
        String id = create("CLASSIC", null).get("id").asText();
        verifyNoInteractions(storage);
        when(storage.createDownloadUrl(anyString(), any())).thenThrow(new IOException("unavailable"));
        JsonNode preview = detail(id);
        assertThat(preview.get("pages").size()).isEqualTo(3);
        assertThat(preview.get("images").isEmpty()).isTrue();
    }

    @Test
    void deletionCascadesOnlyGeneratedPagesAndKeepsSourceRecordsAndImages() throws Exception {
        DiaryEntry entry = entry(0, "Source"); Image image = image(entry);
        UUID id = UUID.fromString(create("CLASSIC", null).get("id").asText());
        mvc.perform(delete(base + "/" + id).with(as(owner.getId()))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from diary_pages where diary_id = ?", Integer.class, id)).isZero();
        assertThat(entries.existsById(entry.getId())).isTrue(); assertThat(images.existsById(image.getId())).isTrue();
        mvc.perform(get(base + "/" + id).with(as(owner.getId()))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
        mvc.perform(delete(base + "/" + id).with(as(owner.getId()))).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test
    void allOperationsCheckAuthenticationAndTripOwnership() throws Exception {
        String id = create("CLASSIC", null).get("id").asText();
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"templateType\":\"CLASSIC\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete(base + "/" + id)).andExpect(status().isUnauthorized());
        UUID stranger = UUID.randomUUID();
        mvc.perform(get(base).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(post(base).with(as(stranger)).contentType(MediaType.APPLICATION_JSON).content("{\"templateType\":\"CLASSIC\"}")).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + id).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(delete(base + "/" + id).with(as(stranger))).andExpect(status().isNotFound());
        Trip other = trips.saveAndFlush(new Trip(owner, "Other", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1), "Asia/Seoul"));
        try {
            String otherBase = base.replace(trip.getId().toString(), other.getId().toString());
            mvc.perform(get(otherBase + "/" + id).with(as(owner.getId()))).andExpect(status().isNotFound());
            mvc.perform(delete(otherBase + "/" + id).with(as(owner.getId()))).andExpect(status().isNotFound());
        } finally { trips.deleteById(other.getId()); }
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsInvalidTemplateAndBlankTitle() throws Exception {
        for (String request : new String[]{"{}", "{\"templateType\":\"UNKNOWN\"}", "{\"templateType\":\"CLASSIC\",\"title\":\"   \"}"}) {
            mvc.perform(post(base).with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        }
        assertThat(diaries.countByTripId(trip.getId())).isZero();
    }

    private JsonNode create(String template, UUID cover) throws Exception {
        String body = "{\"templateType\":\"" + template + "\"" + (cover == null ? "" : ",\"coverImageId\":\"" + cover + "\"") + "}";
        return json.readTree(mvc.perform(post(base).with(as(owner.getId())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode detail(String id) throws Exception {
        return json.readTree(mvc.perform(get(base + "/" + id).with(as(owner.getId()))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString());
    }
    private DiaryEntry entry(int day, String text) { return entries.saveAndFlush(new DiaryEntry(trip.getDays().get(day), "Memory", text, null, null)); }
    private Image image(DiaryEntry entry) { return images.saveAndFlush(new Image(entry, "photo.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 100, StorageType.S3)); }
    private RequestPostProcessor as(UUID id) { return jwt().jwt(token -> token.subject(id.toString()).claim("token_type", "access")); }
}
