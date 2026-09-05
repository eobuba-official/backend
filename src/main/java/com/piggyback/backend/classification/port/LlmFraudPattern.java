package com.piggyback.backend.classification.port;

public record LlmFraudPattern(
        String type,
        String evidence,
        String explanation
) {
}
