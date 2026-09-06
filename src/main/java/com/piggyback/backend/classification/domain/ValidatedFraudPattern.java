package com.piggyback.backend.classification.domain;

import java.util.Objects;

public record ValidatedFraudPattern(
        FraudPatternType type,
        String evidence,
        String explanation
) {
    private static final int MAX_TEXT_LENGTH = 500;

    public ValidatedFraudPattern {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        evidence = evidence.trim();
        explanation = explanation.trim();
        if (evidence.isEmpty() || evidence.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("evidence must contain between 1 and 500 characters");
        }
        if (explanation.isEmpty() || explanation.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("explanation must contain between 1 and 500 characters");
        }
    }
}
