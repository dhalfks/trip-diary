package com.tripdiary.diary;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.UUID;
import com.tripdiary.trip.Trip;
import com.tripdiary.trip.TripRepository;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class DiaryEntryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired TripRepository trips; @Autowired PasswordEncoder encoder;
    UUID ownerId; Trip trip;

    @BeforeEach
    void setUp() {
        User owner = users.saveAndFlush(new User("diary-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "owner"));
        ownerId = owner.getId();
        trip = trips.saveAndFlush(new Trip(owner, "Jeju", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "Asia/Seoul"));
    }

    @Test
    void createsUpdatesListsAndDeletesEntryWithOptionalLinks() throws Exception {
        UUID dayId = trip.getDays().get(0).getId();
        String placeId = body(mvc.perform(post("/api/v1/trips/{tripId}/places", trip.getId()).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"성산일출봉\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String itineraryId = body(mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"일출 보기\",\"placeId\":\"" + placeId + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        String entryId = body(mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/entries", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"제주의 아침\",\"content\":\"정말 아름다운 일출이었다.\",\"itineraryId\":\"" + itineraryId + "\",\"placeId\":\"" + placeId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.itinerary.name").value("일출 보기"))
                .andExpect(jsonPath("$.place.name").value("성산일출봉")).andReturn()).get("id").asText();

        mvc.perform(put("/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}", trip.getId(), dayId, entryId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정한 기록\",\"content\":\"내용도 수정했다.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("수정한 기록"));
        mvc.perform(get("/api/v1/trips/{tripId}/days/{dayId}/entries", trip.getId(), dayId).with(jwtFor(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("내용도 수정했다."));
        mvc.perform(delete("/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}", trip.getId(), dayId, entryId).with(jwtFor(ownerId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsBlankContentForeignLinksAndAnotherUsersAccess() throws Exception {
        UUID dayId = trip.getDays().get(0).getId();
        mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/entries", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"제목\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        UUID otherDayId = trip.getDays().get(1).getId();
        String otherItineraryId = body(mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), otherDayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"다른 날짜 일정\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/entries", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"제목\",\"content\":\"내용\",\"itineraryId\":\"" + otherItineraryId + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DIARY_ENTRY_LINK_MISMATCH"));

        UUID stranger = users.saveAndFlush(new User("stranger-diary-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "stranger")).getId();
        mvc.perform(get("/api/v1/trips/{tripId}/days/{dayId}/entries", trip.getId(), dayId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound());
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UUID id) { return jwt().jwt(token -> token.subject(id.toString()).claim("token_type", "access")); }
}
