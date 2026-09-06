package com.piggyback.backend.classification.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "piggyback.classification")
public class ClassificationProperties {

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double confidenceThreshold = 0.75;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double candidateFloor = 0.35;

    @Min(2)
    @Max(3)
    private int candidateLimit = 3;

    public void validateRange() {
        if (candidateFloor >= confidenceThreshold) {
            throw new IllegalStateException("candidate-floor must be lower than confidence-threshold");
        }
    }
}
