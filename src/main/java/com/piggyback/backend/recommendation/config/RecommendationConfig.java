package com.piggyback.backend.recommendation.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecommendationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
