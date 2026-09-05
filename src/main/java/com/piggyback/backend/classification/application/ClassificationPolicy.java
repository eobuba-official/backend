package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ClassificationSignal;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.domain.TaskTypeView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ClassificationPolicy {

    private static final int MINIMUM_CANDIDATE_COUNT = 2;

    private final ClassificationProperties properties;

    public ClassificationPolicy(ClassificationProperties properties) {
        properties.validateRange();
        this.properties = properties;
    }

    public ClassificationResult normalize(ClassificationCommand command, ClassificationSignal signal) {
        double confidence = normalizeConfidence(signal.confidence());
        String correctedUtterance = normalizeCorrectedUtterance(
                signal.correctedUtterance(),
                command.utterance()
        );
        boolean sttRecheckNeeded = command.inputMethod() == InputMethod.VOICE;

        var intent = TaskTypeCode.fromExternalValue(signal.intent());
        if (confidence >= properties.getConfidenceThreshold() && intent.isPresent()) {
            return ClassificationResult.confirmed(
                    correctedUtterance,
                    confidence,
                    intent.get(),
                    sttRecheckNeeded
            );
        }

        if (confidence >= properties.getCandidateFloor()
                && confidence < properties.getConfidenceThreshold()) {
            List<TaskTypeView> candidates = normalizeCandidates(signal);
            if (candidates.size() >= MINIMUM_CANDIDATE_COUNT) {
                return ClassificationResult.candidates(
                        correctedUtterance,
                        confidence,
                        candidates,
                        sttRecheckNeeded
                );
            }
        }

        return ClassificationResult.unclassified(
                correctedUtterance,
                confidence,
                sttRecheckNeeded
        );
    }

    private List<TaskTypeView> normalizeCandidates(ClassificationSignal signal) {
        LinkedHashSet<TaskTypeCode> uniqueCodes = new LinkedHashSet<>();
        TaskTypeCode.fromExternalValue(signal.intent()).ifPresent(uniqueCodes::add);
        signal.candidates().stream()
                .map(TaskTypeCode::fromExternalValue)
                .flatMap(java.util.Optional::stream)
                .forEach(uniqueCodes::add);

        List<TaskTypeView> result = new ArrayList<>();
        for (TaskTypeCode code : uniqueCodes) {
            if (result.size() == properties.getCandidateLimit()) {
                break;
            }
            result.add(TaskTypeView.from(code));
        }
        return List.copyOf(result);
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN() || confidence.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private String normalizeCorrectedUtterance(String correctedUtterance, String originalUtterance) {
        if (correctedUtterance == null || correctedUtterance.isBlank()) {
            return originalUtterance;
        }
        return correctedUtterance.trim();
    }
}
