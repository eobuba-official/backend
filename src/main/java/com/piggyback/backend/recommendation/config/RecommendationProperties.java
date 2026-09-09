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

    /** 거리(km)를 도보 시간(분)으로 환산할 때 쓰는 보행 속도. 시니어 기준 4km/h */
    private double walkingSpeedKmh = 4.0;
    private int resultLimit = 3;
    private double searchRadiusKm = 10.0;
    private int defaultWaitMinutes = 15;
    private int planningBusinessDays = 2;
}
