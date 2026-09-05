package com.piggyback.backend.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

    private final DistanceCalculator distanceCalculator = new DistanceCalculator();

    @Test
    void calculatesHaversineDistanceInKilometers() {
        double distance = distanceCalculator.calculateKm(37.5665, 126.9780, 37.5700, 126.9820);

        assertThat(distance).isBetween(0.50, 0.55);
    }
}
