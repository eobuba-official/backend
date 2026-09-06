package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Component
public class FraudDetectionPolicy {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionPolicy.class);
    private static final int MAX_EVIDENCE_LENGTH = 500;
    private static final int MAX_EXPLANATION_LENGTH = 500;

    public List<ValidatedFraudPattern> evaluate(String utterance, LlmAnalysisOutput output) {
        List<ValidatedFraudPattern> patterns = normalize(utterance, output.fraudPatterns());

        if (output.fraudDetected() == patterns.isEmpty()) {
            log.warn(
                    "LLM fraud signal normalized: reportedDetected={}, effectiveDetected={}, reportedPatternCount={}, validPatternCount={}",
                    output.fraudDetected(),
                    !patterns.isEmpty(),
                    output.fraudPatterns().size(),
                    patterns.size()
            );
        }
        return patterns;
    }

    private List<ValidatedFraudPattern> normalize(
            String utterance,
            List<LlmFraudPattern> patterns
    ) {
        if (utterance == null || utterance.isBlank() || patterns == null || patterns.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<PatternKey, ValidatedFraudPattern> uniquePatterns = new LinkedHashMap<>();
        for (LlmFraudPattern pattern : patterns) {
            normalizePattern(utterance, pattern).ifPresent(validated ->
                    uniquePatterns.putIfAbsent(
                            new PatternKey(validated.type(), validated.evidence()),
                            validated
                    )
            );
        }
        return List.copyOf(uniquePatterns.values());
    }

    private Optional<ValidatedFraudPattern> normalizePattern(
            String utterance,
            LlmFraudPattern pattern
    ) {
        if (pattern == null) {
            return Optional.empty();
        }
        var type = FraudPatternType.fromExternalValue(pattern.type());
        if (type.isEmpty() || pattern.evidence() == null) {
            return Optional.empty();
        }

        String evidence = pattern.evidence().trim();
        if (evidence.isEmpty()
                || evidence.length() > MAX_EVIDENCE_LENGTH
                || !utterance.contains(evidence)) {
            return Optional.empty();
        }

        String explanation = normalizeExplanation(pattern.explanation(), type.get());
        return Optional.of(new ValidatedFraudPattern(type.get(), evidence, explanation));
    }

    private String normalizeExplanation(String explanation, FraudPatternType type) {
        if (explanation == null || explanation.isBlank()) {
            return type.defaultExplanation();
        }
        String trimmed = explanation.trim();
        return trimmed.length() <= MAX_EXPLANATION_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_EXPLANATION_LENGTH);
    }

    private record PatternKey(FraudPatternType type, String evidence) {
    }
}
