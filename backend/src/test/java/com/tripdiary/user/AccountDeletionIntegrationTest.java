package com.tripdiary.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripdiary.auth.TokenService;
import com.tripdiary.book.*;
import com.tripdiary.diary.*;
import com.tripdiary.image.*;
import com.tripdiary.itinerary.*;
import com.tripdiary.storage.PresignedObjectStorageService;
import com.tripdiary.storage.StorageType;
import com.tripdiary.trip.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AccountDeletionIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired UserRepository users;
    @Autowired TripRepository trips; @Autowired DiaryEntryRepository entries; @Autowired ImageRepository images;
    @Autowired PlaceRepository places; @Autowired ItineraryRepository itineraries;
    @Autowired TravelDiaryService books; @Autowired TokenService tokens;
    @MockitoBean PresignedObjectStorageService storage;
    final List<UUID> createdUsers = new ArrayList<>();
    ListAppender<ILoggingEvent> logs;
    Logger logger;

    @BeforeEach void setUp() {
        when(storage.storageType()).thenReturn(StorageType.S3);
        logger = (Logger) LoggerFactory.getLogger(AccountDeletionService.class);
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }

    @AfterEach void cleanUp() {
        logger.detachAppender(logs); logs.stop();
        for (UUID id : createdUsers) if (users.existsById(id)) users.deleteById(id);
    }

    @Test void deletesOwnedGraphAndEverySessionWhilePreservingOtherUsers() throws Exception {
        Fixture owner = fixture(); Fixture other = fixture();
        var secondSession = tokens.issue(owner.user());
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(users.existsById(owner.user().getId())).isFalse();
            return null;
        }).when(storage).delete(anyString());
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertGraph(owner, false); assertGraph(other, true);
        verify(storage).delete(owner.completed().getStorageKey());
        verify(storage).delete(owner.pending().getStorageKey());
        verify(storage, never()).delete(other.completed().getStorageKey());
        verify(storage, never()).delete(other.pending().getStorageKey());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, owner.user().getId())).isZero();
        for (String access : List.of(owner.session().accessToken(), secondSession.accessToken())) {
            for (String path : List.of("/api/v1/users/me", "/api/v1/trips", "/api/v1/trips/" + owner.trip().getId() + "/diaries")) {
                mvc.perform(get(path).header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
            }
        }
        rejectRefresh(owner.session().refreshToken()); rejectRefresh(secondSession.refreshToken());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(other))).andExpect(status().isOk());
    }

    @Test void missingAuthenticationCannotDeleteAnything() throws Exception {
        Fixture owner = fixture();
        mvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertGraph(owner, true); verifyNoInteractions(storage);
    }

    @Test void deletedUserCannotDeleteAgainWithAnOtherwiseValidJwt() throws Exception {
        Fixture owner = fixture();
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(storage, times(2)).delete(anyString());
    }

    @Test void deletesAccountWithoutTripsOrPhotosWithoutStorage() throws Exception {
        User user = user(); var session = tokens.issue(user);
        mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isNoContent());
        assertThat(users.existsById(user.getId())).isFalse(); verifyNoInteractions(storage);
    }

    @Test void s3FailureDoesNotUndoDbDeletionAndOtherImagesAreStillAttempted() throws Exception {
        Fixture owner = fixture();
        doThrow(new IOException("secret-jwt-or-signed-url-must-not-be-logged")).when(storage).delete(owner.completed().getStorageKey());
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        assertGraph(owner, false);
        verify(storage).delete(owner.pending().getStorageKey());
        rejectRefresh(owner.session().refreshToken());
        String output = logText();
        assertThat(output).contains("ACCOUNT_DELETION_S3_RETRY", "retryRequired=1", "ACCOUNT_DELETION_DB_COMMITTED")
                .doesNotContain("secret-jwt-or-signed-url-must-not-be-logged", owner.user().getEmail(), "private-original-name.jpg", owner.session().accessToken(), owner.session().refreshToken());
        assertThat(logs.list).allMatch(event -> event.getThrowableProxy() == null);
    }

    @Test void runtimeStorageFailureAlsoLeavesRecoverableManifest() throws Exception {
        Fixture owner = fixture();
        doThrow(new IllegalStateException("credential-secret")).when(storage).delete(anyString());
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        assertGraph(owner, false);
        assertThat(logText()).contains("ACCOUNT_DELETION_OBJECT", "keyBase64=", "retryRequired=2").doesNotContain("credential-secret");
    }

    @Test void alreadyMissingObjectsAreSafeToDelete() throws Exception {
        // ObjectStorageService.delete is idempotent for missing S3 keys (covered by its existing SDK tests).
        Fixture owner = fixture(); doNothing().when(storage).delete(anyString());
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        assertGraph(owner, false); assertThat(logText()).contains("retryRequired=0");
    }

    @Test void dbFailureRollsBackUserDataAndNeverTouchesStorage() throws Exception {
        Fixture owner = fixture();
        jdbc.execute("create table account_deletion_test_guard (user_id uuid references users(id))");
        try {
            jdbc.update("insert into account_deletion_test_guard values (?)", owner.user().getId());
            mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(owner)))
                    .andExpect(status().isInternalServerError());
            assertGraph(owner, true); verifyNoInteractions(storage);
            assertThat(logText()).doesNotContain("ACCOUNT_DELETION_DB_COMMITTED");
        } finally { jdbc.execute("drop table account_deletion_test_guard"); }
    }

    private Fixture fixture() {
        User user = user();
        Trip trip = trips.saveAndFlush(new Trip(user, "Private trip", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "Asia/Seoul"));
        Place place = places.saveAndFlush(new Place(trip, "Private place", "Private address", null, null));
        Itinerary itinerary = itineraries.saveAndFlush(new Itinerary(trip.getDays().get(0), "Private plan", "Private notes", null, null, place, 0));
        DiaryEntry entry = entries.saveAndFlush(new DiaryEntry(trip.getDays().get(0), "Memory", "Private text", itinerary, place));
        Image complete = images.saveAndFlush(new Image(entry, "private-original-name.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 100, StorageType.S3));
        Image pending = images.saveAndFlush(Image.pending(entry, "pending.jpg", "images/" + UUID.randomUUID(), "image/jpeg", 100, StorageType.S3));
        var book = books.create(user.getId(), trip.getId(), new TravelDiaryService.GenerateCommand(null, TemplateType.PHOTO, null));
        return new Fixture(user, trip, place, itinerary, entry, complete, pending, book.id(), tokens.issue(user));
    }
    private User user() {
        User user = users.saveAndFlush(new User("deletion-" + UUID.randomUUID() + "@example.test", "test-hash", "Private nickname"));
        createdUsers.add(user.getId()); return user;
    }
    private void assertGraph(Fixture fixture, boolean exists) {
        assertThat(users.existsById(fixture.user().getId())).isEqualTo(exists);
        for (var row : List.of(new Row("trips", fixture.trip().getId()), new Row("places", fixture.place().getId()),
                new Row("itineraries", fixture.itinerary().getId()), new Row("diary_entries", fixture.entry().getId()),
                new Row("images", fixture.completed().getId()), new Row("images", fixture.pending().getId()), new Row("travel_diaries", fixture.book()))) {
            assertThat(jdbc.queryForObject("select count(*) from " + row.table() + " where id = ?", Integer.class, row.id())).isEqualTo(exists ? 1 : 0);
        }
        assertThat(jdbc.queryForObject("select count(*) from trip_days where trip_id = ?", Integer.class, fixture.trip().getId())).isEqualTo(exists ? 2 : 0);
        assertThat(jdbc.queryForObject("select count(*) from diary_pages where diary_id = ?", Integer.class, fixture.book())).isEqualTo(exists ? 2 : 0);
    }
    private void rejectRefresh(String refresh) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
    private String logText() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b); }
    private String bearer(Fixture fixture) { return "Bearer " + fixture.session().accessToken(); }
    private record Row(String table, UUID id) {}
    private record Fixture(User user, Trip trip, Place place, Itinerary itinerary, DiaryEntry entry,
                           Image completed, Image pending, UUID book, TokenService.TokenResponse session) {}
}
