package com.tripdiary.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
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
class AuthIntegrationTest {

    private static final String PASSWORD = "secure-password-123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    @Qualifier("accessTokenEncoder")
    private JwtEncoder accessTokenEncoder;

    @Test
    void signsUpAndRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("여행자"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt", not(blankOrNullString())));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void logsInAndReturnsCurrentUserForValidAccessToken() throws Exception {
        String email = signup();
        Tokens tokens = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("여행자"));

        assertFalse(refreshTokens.findAll().get(0).getTokenHash().contains(tokens.refreshToken()));
        assertEquals(64, refreshTokens.findAll().get(0).getTokenHash().length());
    }

    @Test
    void returnsSameSafeErrorForUnknownEmailAndWrongPassword() throws Exception {
        String email = signup();

        assertInvalidCredentials(email, "wrong-password");
        assertInvalidCredentials(uniqueEmail(), "wrong-password");
    }

    @Test
    void rotatesRefreshTokenAndRevokesAllSessionsWhenOldTokenIsReused() throws Exception {
        Tokens first = login(signup(), PASSWORD);
        Tokens second = refresh(first.refreshToken(), 200, null);
        assertNotEquals(first.refreshToken(), second.refreshToken());

        refresh(first.refreshToken(), 401, "REFRESH_TOKEN_REUSED");
        refresh(second.refreshToken(), 401, "REFRESH_TOKEN_REUSED");
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Tokens tokens = login(signup(), PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(tokens.refreshToken())))
                .andExpect(status().isNoContent());

        refresh(tokens.refreshToken(), 401, "REFRESH_TOKEN_REUSED");
    }

    @Test
    void rejectsMissingTamperedExpiredAndWrongTypeAccessTokens() throws Exception {
        Tokens tokens = login(signup(), PASSWORD);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tamper(tokens.accessToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredAccessToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedRefreshToken() throws Exception {
        Tokens tokens = login(signup(), PASSWORD);
        refresh(tamper(tokens.refreshToken()), 401, "INVALID_REFRESH_TOKEN");
    }

    @Test
    void redactsPasswordsAndTokensFromDtoStringRepresentations() {
        String password = "must-not-appear";
        String token = "token-must-not-appear";

        assertFalse(new AuthController.LoginRequest("user@example.com", password)
                .toString().contains(password));
        assertFalse(new AuthController.SignupRequest("user@example.com", password, "user")
                .toString().contains(password));
        assertFalse(new AuthController.RefreshRequest(token).toString().contains(token));
        String tokenResponse = new TokenService.TokenResponse(token, token, "Bearer", 900, 2592000)
                .toString();
        assertFalse(tokenResponse.contains(token));
        assertTrue(tokenResponse.contains("[REDACTED]"));
    }

    private String signup() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private Tokens login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return tokens(result);
    }

    private Tokens refresh(String refreshToken, int expectedStatus, String expectedCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        if (expectedCode != null) {
            JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
            assertEquals(expectedCode, error.get("code").asText());
            return null;
        }
        return tokens(result);
    }

    private void assertInvalidCredentials(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(email, password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    private Tokens tokens(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Tokens(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private String expiredAccessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .claim(TokenService.TOKEN_TYPE_CLAIM, TokenService.ACCESS_TOKEN_TYPE)
                .build();
        return accessTokenEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String signupJson(String email) throws Exception {
        return objectMapper.writeValueAsString(new SignupBody(email, PASSWORD, " 여행자 "));
    }

    private String refreshJson(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(new RefreshBody(refreshToken));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String tamper(String token) {
        int index = token.length() / 2;
        char replacement = token.charAt(index) == 'a' ? 'b' : 'a';
        return token.substring(0, index) + replacement + token.substring(index + 1);
    }

    private record SignupBody(String email, String password, String nickname) {
    }

    private record LoginBody(String email, String password) {
    }

    private record RefreshBody(String refreshToken) {
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
