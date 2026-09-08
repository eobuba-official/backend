package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ClassificationSignal;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CorrectionConfirmationWorkflow {

    private final TaskClassificationClient classificationClient;
    private final ClassificationPolicy classificationPolicy;
    private final FraudDetectionPolicy fraudDetectionPolicy;
    private final ClassificationResultStore resultStore;
    private final VisitDecisionService visitDecisionService;

    public CorrectionConfirmationWorkflow(
            TaskClassificationClient classificationClient,
            ClassificationPolicy classificationPolicy,
            FraudDetectionPolicy fraudDetectionPolicy,
            ClassificationResultStore resultStore,
            VisitDecisionService visitDecisionService
    ) {
        this.classificationClient = classificationClient;
        this.classificationPolicy = classificationPolicy;
        this.fraudDetectionPolicy = fraudDetectionPolicy;
        this.resultStore = resultStore;
        this.visitDecisionService = visitDecisionService;
    }

    public AnalyzeResult confirm(
            long userId,
            UUID consultationId,
            String confirmedUtterance,
            String taskTypeCode
    ) {
        if (userId <= 0 || consultationId == null
                || confirmedUtterance == null || confirmedUtterance.isBlank()) {
            throw new IllegalArgumentException("userId, consultationId and confirmedUtterance are required");
        }

        String normalizedUtterance = confirmedUtterance.trim();
        var context = resultStore.findCorrectionConfirmation(userId, consultationId);
        requireReady(context.outcome());

        TaskTypeCode selectedTask = parseSelectedTask(taskTypeCode);
        var llmAnalysis = classificationClient.analyze(normalizedUtterance);
        var trustedSignal = new ClassificationSignal(
                normalizedUtterance,
                llmAnalysis.intent(),
                llmAnalysis.confidence(),
                llmAnalysis.candidates()
        );
        var reanalyzed = classificationPolicy.normalize(
                new ClassificationCommand(normalizedUtterance, InputMethod.TEXT, null),
                trustedSignal
        );
        var result = resolveResult(
                context.originalUtterance(),
                normalizedUtterance,
                reanalyzed,
                selectedTask
        );
        var fraudPatterns = fraudDetectionPolicy.evaluate(normalizedUtterance, llmAnalysis);
        VisitDecisionView visitDecision = fraudPatterns.isEmpty() && result.task() != null
                ? VisitDecisionView.from(visitDecisionService.decide(result.task().taskTypeCode()))
                : null;

        var completion = resultStore.completeCorrectionConfirmation(
                userId,
                consultationId,
                normalizedUtterance,
                result,
                fraudPatterns
        );
        requireCompleted(completion);

        if (!fraudPatterns.isEmpty()) {
            return AnalyzeResult.suspended(consultationId, result, fraudPatterns);
        }
        return AnalyzeResult.normal(consultationId, result, visitDecision);
    }

    private TaskTypeCode parseSelectedTask(String taskTypeCode) {
        if (taskTypeCode == null) {
            return null;
        }
        if (taskTypeCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업무 코드는 비어 있을 수 없습니다.");
        }
        return TaskTypeCode.fromExternalValue(taskTypeCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TASK_TYPE_NOT_FOUND,
                        "선택할 수 없는 업무 코드입니다."
                ));
    }

    private ClassificationResult resolveResult(
            String originalUtterance,
            String confirmedUtterance,
            ClassificationResult reanalyzed,
            TaskTypeCode selectedTask
    ) {
        if (selectedTask != null) {
            return ClassificationResult.confirmed(
                    originalUtterance,
                    confirmedUtterance,
                    reanalyzed.confidence(),
                    selectedTask,
                    false
            );
        }
        return switch (reanalyzed.status()) {
            case CONFIRMED -> ClassificationResult.confirmed(
                    originalUtterance,
                    confirmedUtterance,
                    reanalyzed.confidence(),
                    reanalyzed.task().taskTypeCode(),
                    false
            );
            case CANDIDATES -> ClassificationResult.candidates(
                    originalUtterance,
                    confirmedUtterance,
                    reanalyzed.confidence(),
                    reanalyzed.candidates(),
                    false
            );
            case UNCLASSIFIED -> ClassificationResult.unclassified(
                    originalUtterance,
                    confirmedUtterance,
                    reanalyzed.confidence(),
                    false
            );
            case SUSPENDED -> throw new IllegalStateException("Policy must not return suspended status");
        };
    }

    private void requireReady(ClassificationResultStore.ConfirmationOutcome outcome) {
        switch (outcome) {
            case READY -> {
            }
            case CONSULTATION_NOT_FOUND -> throw new BusinessException(
                    ErrorCode.CONSULTATION_NOT_FOUND,
                    "상담을 찾을 수 없습니다."
            );
            case INVALID_STATE, COMPLETED -> throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    "보정 문장을 확인할 수 있는 상담 상태가 아닙니다."
            );
        }
    }

    private void requireCompleted(ClassificationResultStore.ConfirmationOutcome outcome) {
        switch (outcome) {
            case COMPLETED -> {
            }
            case CONSULTATION_NOT_FOUND -> throw new BusinessException(
                    ErrorCode.CONSULTATION_NOT_FOUND,
                    "상담을 찾을 수 없습니다."
            );
            case INVALID_STATE, READY -> throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    "이미 처리되었거나 보정 문장을 확인할 수 없는 상담입니다."
            );
        }
    }
}
