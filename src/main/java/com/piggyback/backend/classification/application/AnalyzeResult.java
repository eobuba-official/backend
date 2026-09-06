package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.VisitDecisionView;

import java.util.List;
import java.util.UUID;

public record AnalyzeResult(
        UUID consultationId,
        String status,
        FraudCheck fraudCheck,
        Classification classification,
        VisitDecisionView visitDecision,
        String guidance
) {
    private static final List<SafetyAction> FRAUD_SAFETY_ACTIONS = List.of(
            new SafetyAction(1, "지금 통화 중이라면 전화를 끊으세요"),
            new SafetyAction(2, "은행 대표번호(1588-9999)로 직접 전화해 확인하세요"),
            new SafetyAction(3, "금융감독원 1332에 상담하세요"),
            new SafetyAction(4, "가족에게 지금 상황을 알리세요")
    );

    public static AnalyzeResult normal(
            UUID consultationId,
            ClassificationResult result,
            VisitDecisionView visitDecision
    ) {
        String consultationStatus = switch (result.status()) {
            case CONFIRMED -> "TASK_CONFIRMED";
            case CANDIDATES -> "CANDIDATES_SUGGESTED";
            case UNCLASSIFIED -> "UNCLASSIFIED";
            case SUSPENDED -> throw new IllegalArgumentException("Unexpected suspended result");
        };
        return new AnalyzeResult(
                consultationId,
                consultationStatus,
                FraudCheck.safe(),
                Classification.from(result),
                visitDecision,
                result.guidance()
        );
    }

    public static AnalyzeResult suspended(
            UUID consultationId,
            ClassificationResult pendingResult,
            List<ValidatedFraudPattern> patterns
    ) {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("Fraud warning requires at least one validated pattern");
        }
        return new AnalyzeResult(
                consultationId,
                "FRAUD_WARNING",
                FraudCheck.detected(patterns),
                Classification.suspended(pendingResult),
                null,
                null
        );
    }

    public record FraudCheck(
            boolean detected,
            boolean dismissible,
            List<FraudPattern> patterns,
            List<SafetyAction> safetyActions,
            Object guardianNotification
    ) {
        private static FraudCheck safe() {
            return new FraudCheck(false, false, List.of(), List.of(), null);
        }

        private static FraudCheck detected(List<ValidatedFraudPattern> patterns) {
            return new FraudCheck(
                    true,
                    true,
                    patterns.stream().map(FraudPattern::from).toList(),
                    FRAUD_SAFETY_ACTIONS,
                    null
            );
        }
    }

    public record FraudPattern(
            String type,
            String label,
            String evidence,
            String explanation
    ) {
        private static FraudPattern from(ValidatedFraudPattern pattern) {
            return new FraudPattern(
                    pattern.type().name(),
                    pattern.type().label(),
                    pattern.evidence(),
                    pattern.explanation()
            );
        }
    }

    public record SafetyAction(int order, String action) {
    }

    public record Classification(
            String status,
            String correctedUtterance,
            Double confidence,
            TaskTypeView task,
            List<TaskTypeView> candidates,
            boolean sttRecheckNeeded
    ) {
        private static Classification from(ClassificationResult result) {
            return new Classification(
                    result.status().name(),
                    result.correctedUtterance(),
                    result.confidence(),
                    result.task(),
                    result.candidates(),
                    result.sttRecheckNeeded()
            );
        }

        private static Classification suspended(ClassificationResult pendingResult) {
            return new Classification(
                    "SUSPENDED",
                    pendingResult.correctedUtterance(),
                    null,
                    null,
                    List.of(),
                    pendingResult.sttRecheckNeeded()
            );
        }
    }
}
