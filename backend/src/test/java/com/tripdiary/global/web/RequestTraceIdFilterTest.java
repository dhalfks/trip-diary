package com.tripdiary.global.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceIdFilterTest {

    private final RequestTraceIdFilter filter = new RequestTraceIdFilter();

    @Test
    void preservesSafeTraceIdInResponseAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceIdFilter.HEADER_NAME, "mobile-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceId = new AtomicReference<>();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                mdcTraceId.set(MDC.get(RequestTraceIdFilter.MDC_KEY)));

        assertEquals("mobile-request-123", response.getHeader(RequestTraceIdFilter.HEADER_NAME));
        assertEquals("mobile-request-123", mdcTraceId.get());
        assertEquals("mobile-request-123", request.getAttribute(RequestTraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    @Test
    void replacesUnsafeTraceIdWithUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceIdFilter.HEADER_NAME, "unsafe trace id\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> { });

        String generated = response.getHeader(RequestTraceIdFilter.HEADER_NAME);
        assertNotEquals("unsafe trace id\n", generated);
        assertTrue(generated.matches("[0-9a-f-]{36}"));
    }
}
