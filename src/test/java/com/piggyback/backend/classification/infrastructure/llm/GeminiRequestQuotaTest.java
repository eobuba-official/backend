package com.piggyback.backend.classification.infrastructure.llm;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiRequestQuotaTest {

    @Test
    void rejectsSixteenthRequestWithinRollingMinute() {
        GeminiProperties properties = properties(15, 500);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);
        GeminiRequestQuota quota = new GeminiRequestQuota(properties, clock);

        GeminiRequestQuota.QuotaUsage usage = null;
        for (int count = 0; count < 15; count++) {
            usage = quota.acquire();
        }

        assertEquals(0, usage.minuteRemaining());
        assertThrows(LlmClassificationException.class, quota::acquire);
    }

    @Test
    void allowsNextRequestAfterRollingMinutePasses() {
        GeminiProperties properties = properties(1, 500);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);
        GeminiRequestQuota quota = new GeminiRequestQuota(properties, clock);
        quota.acquire();

        clock.advanceSeconds(60);

        assertEquals(0, quota.acquire().minuteRemaining());
    }

    @Test
    void rejectsRequestWhenDailyLimitIsReached() {
        GeminiProperties properties = properties(100, 2);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);
        GeminiRequestQuota quota = new GeminiRequestQuota(properties, clock);
        quota.acquire();
        GeminiRequestQuota.QuotaUsage usage = quota.acquire();

        assertEquals(0, usage.dailyRemaining());
        assertThrows(LlmClassificationException.class, quota::acquire);
    }

    @Test
    void resetsDailyLimitAtConfiguredTimezoneMidnight() {
        GeminiProperties properties = properties(100, 1);
        properties.setQuotaZone("America/Los_Angeles");
        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-07T06:59:59Z"),
                ZoneId.of("America/Los_Angeles")
        );
        GeminiRequestQuota quota = new GeminiRequestQuota(properties, clock);
        quota.acquire();

        clock.advanceSeconds(1);

        assertEquals(0, quota.acquire().dailyRemaining());
    }

    private GeminiProperties properties(int requestsPerMinute, int requestsPerDay) {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setRequestsPerMinute(requestsPerMinute);
        properties.setRequestsPerDay(requestsPerDay);
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
