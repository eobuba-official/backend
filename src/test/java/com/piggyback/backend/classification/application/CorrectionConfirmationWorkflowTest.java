package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrectionConfirmationWorkflowTest {

    private static final String ORIGINAL = "셀픽스 의료진 등 좋은 울려 버렸어 아동 장 풍전";
    private static final String CONFIRMED = "통장을 잃어버려서 다시 만들고 싶어";

    @Test
    void confirmsUserSelectedTaskAndReturnsVisitDecisionOnTheSameConsultation() {
        UUID consultationId = UUID.randomUUID();
        ClassificationResultStore store = readyStore(consultationId);
        VisitDecisionService visitDecisionService = visitDecisionService();
        var workflow = workflow(store, safeOutput("", 0.4, List.of()), visitDecisionService);

        var result = workflow.confirm(7L, consultationId, CONFIRMED, "PASSBOOK_REISSUE");

        assertEquals(consultationId, result.consultationId());
        assertEquals("TASK_CONFIRMED", result.status());
        assertEquals(TaskTypeCode.PASSBOOK_REISSUE, result.classification().task().taskTypeCode());
        assertEquals(VisitDecision.VISIT_REQUIRED, result.visitDecision().decision());
        assertEquals(
                "통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다.",
                result.visitDecision().reason()
        );
        verify(store).completeCorrectionConfirmation(
                eq(7L),
                eq(consultationId),
                eq(CONFIRMED),
                any(ClassificationResult.class),
                eq(List.of())
        );
    }

    @Test
    void reclassifiesTheConfirmedUtteranceWhenTaskCodeIsOmitted() {
        UUID consultationId = UUID.randomUUID();
        ClassificationResultStore store = readyStore(consultationId);
        var workflow = workflow(
                store,
                safeOutput("PASSBOOK_REISSUE", 0.93, List.of()),
                visitDecisionService()
        );

        var result = workflow.confirm(7L, consultationId, CONFIRMED, null);

        assertEquals("TASK_CONFIRMED", result.status());
        assertEquals(ORIGINAL, result.classification().originalUtterance());
        assertEquals(CONFIRMED, result.classification().correctedUtterance());
        assertEquals(false, result.classification().sttRecheckNeeded());
    }

    @Test
    void fraudWarningStillTakesPriorityOverAUserSelectedTask() {
        UUID consultationId = UUID.randomUUID();
        ClassificationResultStore store = readyStore(consultationId);
        VisitDecisionService visitDecisionService = mock(VisitDecisionService.class);
        var output = new LlmAnalysisOutput(
                "test-model",
                "test-prompt",
                CONFIRMED,
                true,
                List.of(new LlmFraudPattern(
                        "URGENCY",
                        "지금 당장",
                        "즉시 행동을 요구했습니다."
                )),
                "PASSBOOK_REISSUE",
                0.9,
                List.of()
        );
        var workflow = workflow(store, output, visitDecisionService);
        String riskyUtterance = "지금 당장 통장을 다시 만들래";

        var result = workflow.confirm(7L, consultationId, riskyUtterance, "PASSBOOK_REISSUE");

        assertEquals("FRAUD_WARNING", result.status());
        assertEquals("SUSPENDED", result.classification().status());
        verify(visitDecisionService, never()).decide(any());
    }

    @Test
    void rejectsAConsultationThatIsNotAwaitingCorrectionConfirmation() {
        UUID consultationId = UUID.randomUUID();
        ClassificationResultStore store = mock(ClassificationResultStore.class);
        when(store.findCorrectionConfirmation(7L, consultationId)).thenReturn(
                ClassificationResultStore.ConfirmationContext.of(
                        ClassificationResultStore.ConfirmationOutcome.INVALID_STATE
                )
        );
        TaskClassificationClient client = mock(TaskClassificationClient.class);
        var workflow = workflow(store, client, mock(VisitDecisionService.class));

        var exception = assertThrows(
                BusinessException.class,
                () -> workflow.confirm(7L, consultationId, CONFIRMED, null)
        );

        assertEquals(ErrorCode.INVALID_STATE, exception.getErrorCode());
        verify(client, never()).analyze(any());
    }

    private CorrectionConfirmationWorkflow workflow(
            ClassificationResultStore store,
            LlmAnalysisOutput output,
            VisitDecisionService visitDecisionService
    ) {
        return workflow(store, utterance -> output, visitDecisionService);
    }

    private CorrectionConfirmationWorkflow workflow(
            ClassificationResultStore store,
            TaskClassificationClient client,
            VisitDecisionService visitDecisionService
    ) {
        return new CorrectionConfirmationWorkflow(
                client,
                new ClassificationPolicy(new ClassificationProperties()),
                new FraudDetectionPolicy(),
                store,
                visitDecisionService
        );
    }

    private ClassificationResultStore readyStore(UUID consultationId) {
        ClassificationResultStore store = mock(ClassificationResultStore.class);
        when(store.findCorrectionConfirmation(7L, consultationId)).thenReturn(
                ClassificationResultStore.ConfirmationContext.ready(ORIGINAL)
        );
        when(store.completeCorrectionConfirmation(
                eq(7L), eq(consultationId), any(), any(), any()
        )).thenReturn(ClassificationResultStore.ConfirmationOutcome.COMPLETED);
        return store;
    }

    private LlmAnalysisOutput safeOutput(String intent, double confidence, List<String> candidates) {
        return new LlmAnalysisOutput(
                "test-model",
                "test-prompt",
                CONFIRMED,
                false,
                List.of(),
                intent,
                confidence,
                candidates
        );
    }

    private VisitDecisionService visitDecisionService() {
        VisitDecisionService service = mock(VisitDecisionService.class);
        when(service.decide(TaskTypeCode.PASSBOOK_REISSUE)).thenReturn(
                new VisitDecisionResponse(
                        TaskTypeCode.PASSBOOK_REISSUE,
                        "통장 재발급",
                        VisitDecision.VISIT_REQUIRED,
                        "통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다.",
                        List.of(),
                        List.of()
                )
        );
        return service;
    }
}
