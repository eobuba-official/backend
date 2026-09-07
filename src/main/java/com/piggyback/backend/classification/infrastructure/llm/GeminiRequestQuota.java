package com.piggyback.backend.classification.infrastructure.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class GeminiRequestQuota {

    private final GeminiProperties properties;
    private final Clock clock;
    private final Deque<Instant> minuteRequests = new ArrayDeque<>();

    private LocalDate quotaDate;
    private int dailyRequests;

    @Autowired
    public GeminiRequestQuota(GeminiProperties properties) {
        this(properties, Clock.system(properties.quotaZoneId()));
    }

    GeminiRequestQuota(GeminiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.quotaDate = LocalDate.now(clock);
    }

    public synchronized QuotaUsage acquire() {
        properties.validate();
        Instant now = clock.instant();
        resetDailyQuotaIfNeeded();
        Instant minuteBoundary = now.minusSeconds(60);
        while (!minuteRequests.isEmpty() && !minuteRequests.peekFirst().isAfter(minuteBoundary)) {
            minuteRequests.removeFirst();
        }

        if (minuteRequests.size() >= properties.getRequestsPerMinute()) {
            throw new LlmClassificationException("Gemini requests-per-minute limit reached");
        }
        if (dailyRequests >= properties.getRequestsPerDay()) {
            throw new LlmClassificationException("Gemini requests-per-day limit reached");
        }

        minuteRequests.addLast(now);
        dailyRequests++;
        return new QuotaUsage(
                properties.getRequestsPerMinute() - minuteRequests.size(),
                properties.getRequestsPerDay() - dailyRequests
        );
    }

    private void resetDailyQuotaIfNeeded() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(quotaDate)) {
            quotaDate = today;
            dailyRequests = 0;
        }
    }

    public record QuotaUsage(int minuteRemaining, int dailyRemaining) {
    }
}
