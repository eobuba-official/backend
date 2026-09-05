package com.piggyback.backend.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RecommendationConfigTest {

    @Test
    void usesKoreaTimeZoneRegardlessOfServerDefault() {
        Clock clock = new RecommendationConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
