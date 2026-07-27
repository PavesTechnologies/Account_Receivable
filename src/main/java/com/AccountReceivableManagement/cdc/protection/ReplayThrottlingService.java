package com.AccountReceivableManagement.cdc.protection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ReplayThrottlingService {

    private static final int DEFAULT_MAX_EVENTS_PER_SECOND = 100;
    private static final int DEFAULT_MAX_EVENTS_PER_MINUTE = 1000;
    private static final long DEFAULT_COOLDOWN_MS = 100;

    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final int maxEventsPerSecond;
    private final int maxEventsPerMinute;
    private final long cooldownMs;

    public ReplayThrottlingService() {
        this(DEFAULT_MAX_EVENTS_PER_SECOND, DEFAULT_MAX_EVENTS_PER_MINUTE, DEFAULT_COOLDOWN_MS);
    }

    public ReplayThrottlingService(int maxEventsPerSecond, int maxEventsPerMinute, long cooldownMs) {
        this.maxEventsPerSecond = maxEventsPerSecond;
        this.maxEventsPerMinute = maxEventsPerMinute;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Check if processing should be throttled for a specific entity type
     */
    public boolean shouldThrottle(String entityType) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(entityType, k -> new RateLimiter());
        return limiter.shouldThrottle();
    }

    /**
     * Record a successful processing event
     */
    public void recordProcessing(String entityType) {
        RateLimiter limiter = rateLimiters.get(entityType);
        if (limiter != null) {
            limiter.recordEvent();
        }
    }

    /**
     * Reset rate limiter for a specific entity type
     */
    public void resetRateLimiter(String entityType) {
        rateLimiters.remove(entityType);
        log.info("Reset rate limiter for entity type: {}", entityType);
    }

    /**
     * Get current processing rate for an entity type
     */
    public double getCurrentRate(String entityType) {
        RateLimiter limiter = rateLimiters.get(entityType);
        return limiter != null ? limiter.getCurrentRate() : 0.0;
    }

    /**
     * Inner class to track rate limiting per entity type
     */
    private class RateLimiter {
        private final AtomicInteger eventsPerSecond = new AtomicInteger(0);
        private final AtomicInteger eventsPerMinute = new AtomicInteger(0);
        private volatile long lastSecondReset = System.currentTimeMillis();
        private volatile long lastMinuteReset = System.currentTimeMillis();
        private volatile long lastEventTime = 0;

        public boolean shouldThrottle() {
            long now = System.currentTimeMillis();

            // Reset counters if time windows have passed
            if (now - lastSecondReset > 1000) {
                eventsPerSecond.set(0);
                lastSecondReset = now;
            }

            if (now - lastMinuteReset > 60000) {
                eventsPerMinute.set(0);
                lastMinuteReset = now;
            }

            // Check cooldown between events
            if (lastEventTime > 0 && now - lastEventTime < cooldownMs) {
                return true;
            }

            // Check rate limits
            if (eventsPerSecond.get() >= maxEventsPerSecond) {
                log.warn("Rate limit exceeded per second for entity type: {}", eventsPerSecond.get());
                return true;
            }

            if (eventsPerMinute.get() >= maxEventsPerMinute) {
                log.warn("Rate limit exceeded per minute for entity type: {}", eventsPerMinute.get());
                return true;
            }

            return false;
        }

        public void recordEvent() {
            eventsPerSecond.incrementAndGet();
            eventsPerMinute.incrementAndGet();
            lastEventTime = System.currentTimeMillis();
        }

        public double getCurrentRate() {
            long now = System.currentTimeMillis();
            long elapsedSeconds = (now - lastMinuteReset) / 1000;
            if (elapsedSeconds == 0) {
                return 0.0;
            }
            return (double) eventsPerMinute.get() / elapsedSeconds;
        }
    }
}
