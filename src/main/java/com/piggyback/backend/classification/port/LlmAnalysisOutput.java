package com.piggyback.backend.classification.port;

import com.piggyback.backend.classification.domain.ClassificationSignal;

import java.util.List;

public record LlmAnalysisOutput(
        String model,
        String promptVersion,
        String correctedText,
        boolean fraudDetected,
        List<LlmFraudPattern> fraudPatterns,
        String intent,
        Double confidence,
        List<String> candidates
) {
    public LlmAnalysisOutput {
        fraudPatterns = fraudPatterns == null ? List.of() : List.copyOf(fraudPatterns);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public ClassificationSignal classificationSignal() {
        return new ClassificationSignal(correctedText, intent, confidence, candidates);
    }
}
