package com.tripdiary.operations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final String IMAGES = "/api/v1/trips/{tripId}/days/{dayId}/entries/{entryId}/images";
    private final RateLimitProperties properties;
    private final InMemoryRateLimiter limiter;
    public RateLimitInterceptor(RateLimitProperties properties, InMemoryRateLimiter limiter) { this.properties = properties; this.limiter = limiter; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled()) return true;
        String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        RateLimitProperties.Rule rule = null; String policy = null; boolean anonymous = false;
        if (request.getMethod().equals("POST")) {
            switch (route) {
                case "/api/v1/auth/signup" -> { rule = properties.signup(); policy = "signup"; anonymous = true; }
                case "/api/v1/auth/login" -> { rule = properties.login(); policy = "login"; anonymous = true; }
                case IMAGES + "/upload-url" -> { rule = properties.uploadUrl(); policy = "upload-url"; }
                case IMAGES + "/{imageId}/complete" -> { rule = properties.complete(); policy = "complete"; }
                case "/api/v1/trips/{tripId}/diaries" -> { rule = properties.diary(); policy = "diary"; }
                default -> { }
            }
        } else if (request.getMethod().equals("DELETE") && route.equals("/api/v1/users/me")) { rule = properties.account(); policy = "account"; }
        if (rule == null) return true;
        String identity;
        if (anonymous) identity = request.getRemoteAddr(); // Never trust caller-supplied X-Forwarded-For.
        else {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) return true;
            identity = jwt.getToken().getSubject();
        }
        var decision = limiter.acquire(policy, identity, rule);
        if (!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
        return true;
    }
}
