package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;

public record TaskClassificationOutcome(
        LlmAnalysisOutput llmAnalysis,
        ClassificationResult classification
) {
}
