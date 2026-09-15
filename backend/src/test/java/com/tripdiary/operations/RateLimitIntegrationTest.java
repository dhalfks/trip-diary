package com.tripdiary.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Duration;
import java.util.UUID;
import com.tripdiary.auth.AuthService;
import com.tripdiary.auth.TokenService;
import com.tripdiary.book.TravelDiaryService;
import com.tripdiary.image.ImageUploadService;
import com.tripdiary.user.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"app.rate-limit.enabled=true", "app.rate-limit.signup.limit=2", "app.rate-limit.signup.window=2s",
        "app.rate-limit.login.limit=1", "app.rate-limit.upload-url.limit=1", "app.rate-limit.complete.limit=2",
        "app.rate-limit.diary.limit=1", "app.rate-limit.account.limit=1"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class RateLimitIntegrationTest {
    @Autowired MockMvc mvc; @Autowired RateLimitProperties properties;
    @MockitoBean AuthService auth;
    @MockitoBean ImageUploadService upload;
    @MockitoBean TravelDiaryService books;
    @MockitoBean AccountDeletionService deletion;

    @Test void loginReturnsNormalResponseThenStandard429WithRetryAfterAndTraceId() throws Exception {
        when(auth.login(anyString(), anyString())).thenReturn(new TokenService.TokenResponse("test-access", "test-refresh", "Bearer", 900, 3600));
        String ip = "192.0.2.1";
        mvc.perform(login(ip)).andExpect(status().isOk());
        mvc.perform(login(ip).header("X-Trace-Id", "rate-limit-test"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.traceId").value("rate-limit-test")).andExpect(jsonPath("$.status").value(429));
        verify(auth, times(1)).login(anyString(), anyString());
    }
    @Test void signupAndLoginLimitsAreIndependentAndEnvironmentPropertiesAreApplied() throws Exception {
        assertThat(properties.signup().limit()).isEqualTo(2);
        assertThat(properties.signup().window()).isEqualTo(Duration.ofSeconds(2));
        String body = "{\"email\":\"rate@example.test\",\"password\":\"test-password123\",\"nickname\":\"tester\"}";
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/auth/signup").with(r -> { r.setRemoteAddr("192.0.2.2"); return r; }).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/signup").with(r -> { r.setRemoteAddr("192.0.2.2"); return r; }).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isTooManyRequests());
        mvc.perform(login("192.0.2.2")).andExpect(status().isOk());
    }
    @Test void forwardedHeadersDoNotBypassIpLimitAndOtherIpsAreIndependent() throws Exception {
        mvc.perform(login("192.0.2.3").header("X-Forwarded-For", "192.0.2.100")).andExpect(status().isOk());
        mvc.perform(login("192.0.2.3").header("X-Forwarded-For", "192.0.2.101")).andExpect(status().isTooManyRequests());
        mvc.perform(login("192.0.2.4")).andExpect(status().isOk());
    }
    @Test void protectedPoliciesUseUserAcrossIpsAndDifferentResourceIds() throws Exception {
        String user = UUID.randomUUID().toString();
        String images = "/api/v1/trips/" + UUID.randomUUID() + "/days/" + UUID.randomUUID() + "/entries/" + UUID.randomUUID() + "/images";
        String metadata = "{\"originalFileName\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":100}";
        mvc.perform(owned(post(images + "/upload-url"), user).content(metadata)).andExpect(status().isCreated());
        mvc.perform(owned(post(images + "/upload-url"), user).with(r -> { r.setRemoteAddr("192.0.2.20"); return r; }).content(metadata)).andExpect(status().isTooManyRequests());
        for (int i = 0; i < 2; i++) mvc.perform(owned(post(images + "/" + UUID.randomUUID() + "/complete"), user)).andExpect(status().isOk());
        mvc.perform(owned(post(images + "/" + UUID.randomUUID() + "/complete"), user)).andExpect(status().isTooManyRequests());
        mvc.perform(owned(post(images + "/upload-url"), UUID.randomUUID().toString()).content(metadata)).andExpect(status().isCreated());
    }
    @Test void diaryAndAccountPoliciesDoNotConsumeEachOthersQuota() throws Exception {
        String user = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) mvc.perform(owned(post("/api/v1/trips/" + UUID.randomUUID() + "/diaries"), user).content("{\"templateType\":\"CLASSIC\"}"))
                .andExpect(status().is(i == 0 ? 201 : 429));
        mvc.perform(owned(delete("/api/v1/users/me"), user)).andExpect(status().isNoContent());
        mvc.perform(owned(delete("/api/v1/users/me"), user)).andExpect(status().isTooManyRequests());
        mvc.perform(delete("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
    private MockHttpServletRequestBuilder login(String ip) { return post("/api/v1/auth/login").with(r -> { r.setRemoteAddr(ip); return r; })
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"rate@example.test\",\"password\":\"test-password123\"}"); }
    private MockHttpServletRequestBuilder owned(MockHttpServletRequestBuilder request, String user) { return request.with(jwt().jwt(token -> token.subject(user).claim("token_type", "access"))).contentType(MediaType.APPLICATION_JSON); }
}
