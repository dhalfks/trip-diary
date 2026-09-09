package com.tripdiary.trip;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private UUID ownerId;
    private UUID otherId;

    @BeforeEach
    void setUpUsers() {
        ownerId = users.saveAndFlush(new User(
                "owner-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("password-123!"), "owner")).getId();
        otherId = users.saveAndFlush(new User(
                "other-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("password-123!"), "other")).getId();
    }

    @Test
    void createsReadsUpdatesAndDeletesTripWithInclusiveDays() throws Exception {
        String tripId = create(ownerId, "Seoul", "2026-10-01", "2026-10-03", "Asia/Seoul");

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).with(jwtFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Seoul"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.days", hasSize(3)))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.days[2].dayNumber").value(3));

        mockMvc.perform(put("/api/v1/trips/{id}", tripId)
                        .with(jwtFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content(request("Busan", "2026-11-10", "2026-11-11", "Asia/Seoul")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Busan"))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[1].date").value("2026-11-11"));

        mockMvc.perform(delete("/api/v1/trips/{id}", tripId).with(jwtFor(ownerId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/trips/{id}", tripId).with(jwtFor(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void neverExposesAnotherUsersTrip() throws Exception {
        String tripId = create(ownerId, "Private", "2026-10-01", "2026-10-01", "UTC");

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).with(jwtFor(otherId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/trips/{id}", tripId).with(jwtFor(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Stolen", "2026-10-01", "2026-10-01", "UTC")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/trips/{id}", tripId).with(jwtFor(otherId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/trips/{id}", tripId).with(jwtFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private"));
    }

    @Test
    void listsOnlyOwnedTripsWithBoundedPagination() throws Exception {
        create(ownerId, "Mine 1", "2026-10-01", "2026-10-01", "UTC");
        create(ownerId, "Mine 2", "2026-10-02", "2026-10-02", "UTC");
        create(otherId, "Not mine", "2026-10-03", "2026-10-03", "UTC");

        mockMvc.perform(get("/api/v1/trips?page=0&size=1").with(jwtFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/trips?size=101").with(jwtFor(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidPeriodAndTimezone() throws Exception {
        mockMvc.perform(post("/api/v1/trips").with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Invalid", "2026-10-02", "2026-10-01", "Asia/Seoul")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRIP_PERIOD"));

        mockMvc.perform(post("/api/v1/trips").with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Invalid", "2026-10-01", "2026-10-01", "Mars/Olympus")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));

        mockMvc.perform(post("/api/v1/trips").with(jwtFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Too long", "2026-01-01", "2027-01-02", "UTC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRIP_PERIOD"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String create(UUID userId, String title, String start, String end, String timezone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trips").with(jwtFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(title, start, end, timezone)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("token_type", "access"));
    }

    private String request(String title, String start, String end, String timezone) {
        return """
                {"title":"%s","startDate":"%s","endDate":"%s","timezone":"%s"}
                """.formatted(title, start, end, timezone);
    }
}
