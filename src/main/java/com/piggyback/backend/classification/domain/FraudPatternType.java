package com.piggyback.backend.classification.domain;

import java.util.Locale;
import java.util.Optional;

public enum FraudPatternType {
    IMPERSONATION,
    SAFE_ACCOUNT,
    SECRECY,
    REMOTE_CONTROL,
    URGENCY;

    public static Optional<FraudPatternType> fromExternalValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
