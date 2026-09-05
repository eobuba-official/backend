package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.InputMethod;

public record ClassificationCommand(
        String utterance,
        InputMethod inputMethod,
        Double sttConfidence
) {
    public ClassificationCommand {
        if (utterance == null || utterance.isBlank()) {
            throw new IllegalArgumentException("utterance must not be blank");
        }
        if (utterance.length() > 1_000) {
            throw new IllegalArgumentException("utterance must be at most 1000 characters");
        }
        if (inputMethod == null) {
            throw new IllegalArgumentException("inputMethod is required");
        }
        if (sttConfidence != null && (sttConfidence < 0.0 || sttConfidence > 1.0)) {
            throw new IllegalArgumentException("sttConfidence must be between 0 and 1");
        }
    }
}
