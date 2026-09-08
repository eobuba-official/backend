package com.piggyback.backend.classification.domain;

import com.piggyback.backend.domain.TaskTypeCode;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

public record ClassificationResult(
        ClassificationStatus status,
        String originalUtterance,
        String correctedUtterance,
        double confidence,
        TaskTypeView task,
        List<TaskTypeView> candidates,
        boolean sttRecheckNeeded,
        String guidance
) {
    public static final String UNCLASSIFIED_GUIDANCE =
            "말씀하신 내용으로는 업무를 찾지 못했어요. 가까운 지점에서 상담받으세요.";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public ClassificationResult {
        if (originalUtterance == null || originalUtterance.isBlank()) {
            throw new IllegalArgumentException("originalUtterance must not be blank");
        }
        if (correctedUtterance == null || correctedUtterance.isBlank()) {
            correctedUtterance = originalUtterance;
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static ClassificationResult confirmed(
            String correctedUtterance,
            double confidence,
            TaskTypeCode taskTypeCode,
            boolean sttRecheckNeeded
    ) {
        return confirmed(
                correctedUtterance,
                correctedUtterance,
                confidence,
                taskTypeCode,
                sttRecheckNeeded
        );
    }

    public static ClassificationResult confirmed(
            String originalUtterance,
            String correctedUtterance,
            double confidence,
            TaskTypeCode taskTypeCode,
            boolean sttRecheckNeeded
    ) {
        return new ClassificationResult(
                ClassificationStatus.CONFIRMED,
                originalUtterance,
                correctedUtterance,
                confidence,
                TaskTypeView.from(taskTypeCode),
                List.of(),
                sttRecheckNeeded,
                null
        );
    }

    public static ClassificationResult candidates(
            String correctedUtterance,
            double confidence,
            List<TaskTypeView> candidates,
            boolean sttRecheckNeeded
    ) {
        return candidates(
                correctedUtterance,
                correctedUtterance,
                confidence,
                candidates,
                sttRecheckNeeded
        );
    }

    public static ClassificationResult candidates(
            String originalUtterance,
            String correctedUtterance,
            double confidence,
            List<TaskTypeView> candidates,
            boolean sttRecheckNeeded
    ) {
        return new ClassificationResult(
                ClassificationStatus.CANDIDATES,
                originalUtterance,
                correctedUtterance,
                confidence,
                null,
                candidates,
                sttRecheckNeeded,
                null
        );
    }

    public static ClassificationResult unclassified(
            String correctedUtterance,
            double confidence,
            boolean sttRecheckNeeded
    ) {
        return unclassified(
                correctedUtterance,
                correctedUtterance,
                confidence,
                sttRecheckNeeded
        );
    }

    public static ClassificationResult unclassified(
            String originalUtterance,
            String correctedUtterance,
            double confidence,
            boolean sttRecheckNeeded
    ) {
        return new ClassificationResult(
                ClassificationStatus.UNCLASSIFIED,
                originalUtterance,
                correctedUtterance,
                confidence,
                null,
                List.of(),
                sttRecheckNeeded,
                UNCLASSIFIED_GUIDANCE
        );
    }

    public boolean correctionApplied() {
        return !normalizeForComparison(originalUtterance)
                .equals(normalizeForComparison(correctedUtterance));
    }

    private static String normalizeForComparison(String utterance) {
        String unicodeNormalized = Normalizer.normalize(utterance, Normalizer.Form.NFC);
        return WHITESPACE.matcher(unicodeNormalized.trim()).replaceAll(" ");
    }
}
