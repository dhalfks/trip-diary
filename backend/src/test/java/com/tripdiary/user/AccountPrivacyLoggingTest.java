package com.tripdiary.user;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripdiary.auth.AuthController;
import com.tripdiary.auth.JwtProperties;
import com.tripdiary.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountPrivacyLoggingTest {
    @Test void authenticationDtosAndConfigurationRedactPersonalDataAndSecrets() {
        String email = "private@example.test", password = "private-password", nickname = "private-nickname";
        assertThat(new AuthController.SignupRequest(email, password, nickname).toString()).doesNotContain(email, password, nickname);
        assertThat(new AuthController.LoginRequest(email, password).toString()).doesNotContain(email, password);
        String access = "test-access-secret-".repeat(3), refresh = "test-refresh-secret-".repeat(3);
        assertThat(new JwtProperties("issuer", "audience", access, refresh, Duration.ofMinutes(15), Duration.ofDays(30)).toString())
                .contains("[REDACTED]").doesNotContain(access, refresh);
    }

    @Test void unexpectedExceptionDoesNotLogItsSensitiveMessageOrCause() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
        try {
            var response = new GlobalExceptionHandler().handleUnexpectedException(
                    new IllegalStateException("password-and-jwt-secret", new RuntimeException("signed-url-secret")), new MockHttpServletRequest());
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.get(0).getFormattedMessage()).contains("IllegalStateException").doesNotContain("password-and-jwt-secret", "signed-url-secret");
            assertThat(logs.list.get(0).getThrowableProxy()).isNull();
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
}
