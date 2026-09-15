package com.tripdiary.global.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String TRACE_ID_ATTRIBUTE = RequestTraceIdFilter.class.getName() + ".traceId";
    public static final String ERROR_CODE_ATTRIBUTE = RequestTraceIdFilter.class.getName() + ".errorCode";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceIdFilter.class);

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER_NAME));
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(HEADER_NAME, traceId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, traceId)) {
            long started = System.nanoTime();
            boolean failed = false;
            try { filterChain.doFilter(request, response); }
            catch (ServletException | IOException | RuntimeException exception) { failed = true; throw exception; }
            finally {
                int status = failed ? 500 : response.getStatus();
                // Framework route templates only: raw paths/query strings can contain personal data or secrets.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String path = route instanceof String template ? template : "<unmapped>";
                String method = java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD").contains(request.getMethod()) ? request.getMethod() : "OTHER";
                Object code = request.getAttribute(ERROR_CODE_ATTRIBUTE);
                long millis = (System.nanoTime() - started) / 1000000;
                if (status >= 400) log.warn("HTTP_REQUEST method={} path={} status={} durationMs={} code={}", method, path, status, millis, code == null ? "UNCLASSIFIED" : code);
                else log.debug("HTTP_REQUEST method={} path={} status={} durationMs={}", method, path, status, millis);
            }
        }
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
