package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.VisitDecisionView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "자연어 업무 분석 결과. 사기 위험이 있으면 일반 업무 결과보다 경고가 우선합니다.")
public record AnalyzeResult(
        @Schema(description = "상담 식별자", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID consultationId,
        @Schema(
                description = "상담 처리 상태",
                allowableValues = {"FRAUD_WARNING", "TASK_CONFIRMED", "CANDIDATES_SUGGESTED", "UNCLASSIFIED"},
                example = "TASK_CONFIRMED"
        )
        String status,
        @Schema(description = "보이스피싱 위험 검사 결과")
        FraudCheck fraudCheck,
        @Schema(description = "정규화된 업무 분류 결과")
        Classification classification,
        @Schema(description = "업무가 확정된 경우의 방문 판단. 그 외에는 null", nullable = true)
        VisitDecisionView visitDecision,
        @Schema(description = "분류 불가 시 가까운 지점 상담 안내", nullable = true)
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
            @Schema(description = "검증된 위험 패턴 존재 여부", example = "false")
            boolean detected,
            @Schema(description = "사용자가 경고 해제 흐름을 선택할 수 있는지 여부", example = "false")
            boolean dismissible,
            @Schema(description = "원문 근거 검증을 통과한 위험 패턴")
            List<FraudPattern> patterns,
            @Schema(description = "경고 화면에 표시할 안전 행동 목록")
            List<SafetyAction> safetyActions,
            @Schema(description = "보호자 알림 결과. 현재 알림 기능 미구현으로 null", nullable = true)
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
            @Schema(description = "위험 패턴 코드", example = "SAFE_ACCOUNT")
            String type,
            @Schema(description = "사용자용 한국어 패턴명", example = "안전계좌 요구")
            String label,
            @Schema(description = "사용자 원문에 실제 포함된 근거", example = "안전계좌로 보내래")
            String evidence,
            @Schema(description = "위험한 이유에 대한 쉬운 설명")
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

    public record SafetyAction(
            @Schema(description = "표시 순서", example = "1") int order,
            @Schema(description = "즉시 실행할 안전 행동") String action
    ) {
    }

    public record Classification(
            @Schema(
                    description = "분류 상태",
                    allowableValues = {"CONFIRMED", "CANDIDATES", "UNCLASSIFIED", "SUSPENDED"},
                    example = "CONFIRMED"
            )
            String status,
            @Schema(description = "STT 오인식을 교정한 확인용 문장")
            String correctedUtterance,
            @Schema(description = "LLM 분류 확신도. 사기 경고에서는 숨김", nullable = true)
            Double confidence,
            @Schema(description = "확정된 업무. 확정 상태가 아니면 null", nullable = true)
            TaskTypeView task,
            @Schema(description = "사용자가 선택할 업무 후보 2~3개")
            List<TaskTypeView> candidates,
            @Schema(description = "음성 입력 문장의 사용자 재확인 필요 여부", example = "true")
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
