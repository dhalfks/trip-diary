package com.tripdiary.operations;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {
    private final Map<Key, Window> windows = new HashMap<>();
    private final RateLimitProperties properties;
    private final Clock clock;
    public InMemoryRateLimiter(RateLimitProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }

    public synchronized Decision acquire(String policy, String identity, RateLimitProperties.Rule rule) {
        if (!properties.enabled()) return new Decision(true, 0);
        Instant now = clock.instant();
        Key key = new Key(policy, identity);
        Window window = windows.get(key);
        if (window != null && !now.isBefore(window.expires)) { windows.remove(key); window = null; }
        if (window == null) {
            // Never evict active counters: doing so would let identity churn bypass existing limits.
            if (windows.size() >= properties.maxKeys()) return new Decision(false, 60);
            window = new Window(now.plus(rule.window())); windows.put(key, window);
        }
        if (window.used >= rule.limit()) {
            long retry = Math.max(1, (java.time.Duration.between(now, window.expires).toMillis() + 999) / 1000);
            return new Decision(false, retry);
        }
        window.used++;
        return new Decision(true, 0);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public synchronized void evictExpired() { Instant now = clock.instant(); windows.values().removeIf(window -> !now.isBefore(window.expires)); }
    synchronized int size() { return windows.size(); }
    public record Decision(boolean allowed, long retryAfterSeconds) {}
    private record Key(String policy, String identity) {}
    private static class Window { final Instant expires; int used; Window(Instant expires) { this.expires = expires; } }
}
