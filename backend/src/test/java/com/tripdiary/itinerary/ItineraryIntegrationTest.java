package com.tripdiary.itinerary;

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
class ItineraryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired TripRepository trips; @Autowired PasswordEncoder encoder;
    UUID ownerId; Trip trip;

    @BeforeEach void setUp() {
        User owner = users.saveAndFlush(new User("schedule-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "owner"));
        ownerId = owner.getId();
        trip = trips.saveAndFlush(new Trip(owner, "Seoul", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), "Asia/Seoul"));
    }

    @Test void managesPlacesAndOrderedItineraries() throws Exception {
        String placeId = body(mvc.perform(post("/api/v1/trips/{tripId}/places", trip.getId()).with(jwtFor(ownerId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"경복궁\",\"address\":\"서울 종로구\",\"latitude\":37.579617,\"longitude\":126.977041}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("경복궁")).andReturn()).get("id").asText();
        UUID dayId = trip.getDays().get(0).getId();
        String firstId = createItem(dayId, "아침", "09:00:00", "10:00:00", placeId);
        String secondId = createItem(dayId, "점심", "12:00:00", "13:00:00", null);

        mvc.perform(put("/api/v1/trips/{tripId}/days/{dayId}/itineraries/order", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itineraryIds\":[\"" + secondId + "\",\"" + firstId + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("점심")).andExpect(jsonPath("$[1].sortOrder").value(1));
        mvc.perform(delete("/api/v1/trips/{tripId}/places/{placeId}", trip.getId(), placeId).with(jwtFor(ownerId))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), dayId).with(jwtFor(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].place").doesNotExist());
    }

    @Test void rejectsInvalidTimeAndAnotherUsersAccess() throws Exception {
        UUID dayId = trip.getDays().get(0).getId();
        mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), dayId).with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"invalid\",\"startTime\":\"11:00:00\",\"endTime\":\"10:00:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ITINERARY_TIME"));
        UUID stranger = users.saveAndFlush(new User("stranger-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "stranger")).getId();
        mvc.perform(get("/api/v1/trips/{tripId}/places", trip.getId()).with(jwtFor(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), dayId).with(jwtFor(stranger))).andExpect(status().isNotFound());
    }

    @Test void rejectsCoordinatePairAndPlaceFromAnotherTrip() throws Exception {
        mvc.perform(post("/api/v1/trips/{tripId}/places", trip.getId()).with(jwtFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"invalid\",\"latitude\":37.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PLACE_COORDINATES"));
        User other = users.saveAndFlush(new User("other-trip-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "other"));
        Trip otherTrip = trips.saveAndFlush(new Trip(other, "Other", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), "UTC"));
        // A place in a different trip must never be accepted, regardless of who owns that trip.
        String foreignId = body(mvc.perform(post("/api/v1/trips/{tripId}/places", otherTrip.getId()).with(jwtFor(other.getId())).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"foreign\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), trip.getDays().get(0).getId()).with(jwtFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"invalid place\",\"placeId\":\"" + foreignId + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLACE_TRIP_MISMATCH"));
    }

    @Test void protectsPlaceSearchAndReportsMissingProviderConfiguration() throws Exception {
        mvc.perform(get("/api/v1/trips/{tripId}/places/search", trip.getId()).with(jwtFor(ownerId)).param("query", "경복궁"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLACE_SEARCH_UNAVAILABLE"));
        UUID stranger = users.saveAndFlush(new User("search-stranger-" + UUID.randomUUID() + "@example.com", encoder.encode("password-123!"), "stranger")).getId();
        mvc.perform(get("/api/v1/trips/{tripId}/places/search", trip.getId()).with(jwtFor(stranger)).param("query", "경복궁"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        mvc.perform(get("/api/v1/trips/{tripId}/places/search", trip.getId()).with(jwtFor(ownerId)).param("query", "가"))
                .andExpect(status().isBadRequest());
    }

    private String createItem(UUID dayId, String title, String start, String end, String placeId) throws Exception {
        String place = placeId == null ? "null" : "\"" + placeId + "\"";
        return body(mvc.perform(post("/api/v1/trips/{tripId}/days/{dayId}/itineraries", trip.getId(), dayId).with(jwtFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"placeId\":" + place + "}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }
    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UUID id) { return jwt().jwt(token -> token.subject(id.toString()).claim("token_type", "access")); }
}
