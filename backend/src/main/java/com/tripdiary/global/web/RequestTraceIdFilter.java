package com.tripdiary.global.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String TRACE_ID_ATTRIBUTE = RequestTraceIdFilter.class.getName() + ".traceId";

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
            filterChain.doFilter(request, response);
        }
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
