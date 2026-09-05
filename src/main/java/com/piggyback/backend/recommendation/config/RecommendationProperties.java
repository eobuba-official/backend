package com.piggyback.backend.recommendation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "piggyback.recommendation")
public class RecommendationProperties {

    private double distanceWeight = 0.4;
    private double waitingWeight = 0.6;
    private int resultLimit = 3;
    private double searchRadiusKm = 10.0;
    private int defaultWaitMinutes = 15;
    private int planningBusinessDays = 2;
}
