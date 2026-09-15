package com.tripdiary.operations;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    private RateLimitProperties config(boolean enabled, int maxKeys) { return new RateLimitProperties(enabled, maxKeys, null, null, null, null, null, null); }
    @Test void enforcesIndependentIdentityAndPolicyCountersAndWindowExpiry() {
        var clock = new MutableClock(); var limiter = new InMemoryRateLimiter(config(true, 10), clock);
        var rule = new RateLimitProperties.Rule(2, Duration.ofSeconds(10));
        assertThat(limiter.acquire("login", "a", rule).allowed()).isTrue();
        assertThat(limiter.acquire("login", "a", rule).allowed()).isTrue();
        assertThat(limiter.acquire("login", "a", rule).retryAfterSeconds()).isEqualTo(10);
        assertThat(limiter.acquire("signup", "a", rule).allowed()).isTrue();
        assertThat(limiter.acquire("login", "b", rule).allowed()).isTrue();
        clock.now = clock.now.plusSeconds(10);
        assertThat(limiter.acquire("login", "a", rule).allowed()).isTrue();
        limiter.evictExpired(); assertThat(limiter.size()).isEqualTo(1);
    }
    @Test void memoryIsBoundedWithoutEvictingActiveLimitsAndExpiresWithoutRequests() {
        var clock = new MutableClock(); var limiter = new InMemoryRateLimiter(config(true, 2), clock);
        var rule = new RateLimitProperties.Rule(1, Duration.ofSeconds(1));
        limiter.acquire("a", "1", rule); limiter.acquire("a", "2", rule);
        for (int i = 0; i < 100; i++) assertThat(limiter.acquire("a", "new-" + i, rule).allowed()).isFalse();
        assertThat(limiter.size()).isEqualTo(2);
        assertThat(limiter.acquire("a", "1", rule).allowed()).isFalse();
        clock.now = clock.now.plusSeconds(1); limiter.evictExpired(); assertThat(limiter.size()).isZero();
        assertThat(limiter.acquire("a", "3", rule).allowed()).isTrue();
    }
    @Test void disabledLimiterDoesNotAllocateState() {
        var limiter = new InMemoryRateLimiter(config(false, 1), Clock.systemUTC());
        for (int i = 0; i < 20; i++) assertThat(limiter.acquire("login", "a", new RateLimitProperties.Rule(1, Duration.ofMinutes(1))).allowed()).isTrue();
        assertThat(limiter.size()).isZero();
    }
    @Test void concurrentRequestsCannotExceedLimit() throws Exception {
        var limiter = new InMemoryRateLimiter(config(true, 10), Clock.systemUTC());
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 100).<Callable<Boolean>>mapToObj(i -> () -> limiter.acquire("login", "a", new RateLimitProperties.Rule(10, Duration.ofMinutes(1))).allowed()).toList();
            int allowed = 0; for (var future : pool.invokeAll(tasks)) if (future.get()) allowed++;
            assertThat(allowed).isEqualTo(10);
        } finally { pool.shutdownNow(); }
    }
    @Test void configurationDefaultsAndUnsafeValuesAreValidated() {
        var defaults = config(true, 20000);
        assertThat(defaults.signup().limit()).isEqualTo(10);
        assertThat(defaults.login().limit()).isEqualTo(30);
        assertThat(defaults.uploadUrl().limit()).isEqualTo(60);
        assertThat(defaults.complete().limit()).isEqualTo(120);
        assertThat(defaults.diary().limit()).isEqualTo(10);
        assertThat(defaults.account().window()).isEqualTo(Duration.ofMinutes(10));
        assertThatThrownBy(() -> config(true, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Rule(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
