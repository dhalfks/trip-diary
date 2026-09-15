package com.tripdiary.operations;

import static org.assertj.core.api.Assertions.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripdiary.global.web.RequestTraceIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class OperationLoggingTest {
    @Test void requestLogsContainOperationalFieldsWithoutHeadersBodiesQueriesOrRawPaths() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestTraceIdFilter.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            var request = new MockHttpServletRequest("POST", "/private-email@example.test");
            request.setQueryString("url=https://private.test?X-Amz-Signature=secret");
            request.setContent("password secret JWT AWS_ACCESS_KEY AWS_SECRET_KEY AWS_SESSION_TOKEN".getBytes());
            request.addHeader("Authorization", "Bearer private-jwt"); request.addHeader("X-Trace-Id", "ops-test");
            var response = new MockHttpServletResponse();
            new RequestTraceIdFilter().doFilter(request, response, (req, res) -> {
                request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/auth/login");
                request.setAttribute(RequestTraceIdFilter.ERROR_CODE_ATTRIBUTE, "RATE_LIMIT_EXCEEDED"); response.setStatus(429);
            });
            assertThat(logs.list).hasSize(1);
            var event = logs.list.get(0);
            assertThat(event.getFormattedMessage()).contains("method=POST", "path=/api/v1/auth/login", "status=429", "durationMs=", "RATE_LIMIT_EXCEEDED")
                    .doesNotContain("password", "secret", "JWT", "AWS_", "private-email", "private-jwt", "X-Amz");
            assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "ops-test");
            assertThat(event.getThrowableProxy()).isNull();
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
    @Test void unmappedFailureDoesNotLogItsRawPathAndSuccessUsesDebug() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestTraceIdFilter.class);
        Level previous = logger.getLevel(); logger.setLevel(Level.INFO);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            var request = new MockHttpServletRequest("GET", "/secret-token"); var response = new MockHttpServletResponse();
            new RequestTraceIdFilter().doFilter(request, response, (req, res) -> response.setStatus(404));
            assertThat(logs.list.get(0).getFormattedMessage()).contains("path=<unmapped>").doesNotContain("secret-token");
            logs.list.clear();
            new RequestTraceIdFilter().doFilter(new MockHttpServletRequest("GET", "/api/v1/health"), new MockHttpServletResponse(), (req, res) -> {});
            assertThat(logs.list).isEmpty();
        } finally { logger.detachAppender(logs); logs.stop(); logger.setLevel(previous); }
    }
}
