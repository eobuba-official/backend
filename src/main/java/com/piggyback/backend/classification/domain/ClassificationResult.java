package com.piggyback.backend.classification.domain;

import com.piggyback.backend.domain.TaskTypeCode;

import java.util.List;

public record ClassificationResult(
        ClassificationStatus status,
        String correctedUtterance,
        double confidence,
        TaskTypeView task,
        List<TaskTypeView> candidates,
        boolean sttRecheckNeeded,
        String guidance
) {
    public static final String UNCLASSIFIED_GUIDANCE =
            "말씀하신 내용으로는 업무를 찾지 못했어요. 가까운 지점에서 상담받으세요.";

    public ClassificationResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static ClassificationResult confirmed(
            String correctedUtterance,
            double confidence,
            TaskTypeCode taskTypeCode,
            boolean sttRecheckNeeded
    ) {
        return new ClassificationResult(
                ClassificationStatus.CONFIRMED,
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
        return new ClassificationResult(
                ClassificationStatus.CANDIDATES,
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
        return new ClassificationResult(
                ClassificationStatus.UNCLASSIFIED,
                correctedUtterance,
                confidence,
                null,
                List.of(),
                sttRecheckNeeded,
                UNCLASSIFIED_GUIDANCE
        );
    }
}
