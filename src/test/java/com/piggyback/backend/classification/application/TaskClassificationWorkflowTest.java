package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskClassificationWorkflowTest {

    @Test
    void classifiesAndStoresANonFraudConsultation() {
        UUID consultationId = UUID.randomUUID();
        var store = new RecordingStore(consultationId);
        var workflow = workflow(false, store);
        var command = new ClassificationCommand("통장을 잃어버렸어", InputMethod.VOICE, null);

        var outcome = workflow.analyze(7L, command);

        assertEquals(consultationId, outcome.consultationId());
        assertEquals("TASK_CONFIRMED", outcome.status());
        assertEquals("VISIT_REQUIRED", outcome.visitDecision().decision());
        assertEquals(7L, store.userId);
        assertEquals(TaskTypeCode.PASSBOOK_REISSUE, store.result.task().taskTypeCode());
    }

    @Test
    void storesFraudDetectedClassificationAsSuspendedWithoutVisitDecision() {
        var store = new RecordingStore(UUID.randomUUID());
        var workflow = workflow(true, store);

        var outcome = workflow.analyze(
                7L,
                new ClassificationCommand("안전계좌로 보내래", InputMethod.TEXT, null)
        );

        assertEquals("FRAUD_WARNING", outcome.status());
        assertEquals("SUSPENDED", outcome.classification().status());
        assertEquals(1, outcome.fraudCheck().patterns().size());
        assertEquals("안전계좌 요구", outcome.fraudCheck().patterns().get(0).label());
        assertEquals(4, outcome.fraudCheck().safetyActions().size());
        assertEquals(1, store.suspendedSaveCalls);
        assertEquals(0, store.saveCalls);
    }

    private TaskClassificationWorkflow workflow(boolean fraudDetected, RecordingStore store) {
        var client = (com.piggyback.backend.classification.port.TaskClassificationClient) utterance ->
                new LlmAnalysisOutput(
                        "test-model",
                        "test-prompt",
                        utterance,
                        fraudDetected,
                        fraudDetected
                                ? List.of(
                                        new LlmFraudPattern(
                                                "SAFE_ACCOUNT",
                                                "안전계좌",
                                                "안전계좌 송금을 요구했습니다."
                                        ),
                                        new LlmFraudPattern(
                                                "UNKNOWN_PATTERN",
                                                "보내래",
                                                "허용되지 않은 패턴입니다."
                                        ),
                                        new LlmFraudPattern(
                                                "URGENCY",
                                                "지금 당장",
                                                "입력에 없는 근거입니다."
                                        )
                                )
                                : List.of(),
                        "PASSBOOK_REISSUE",
                        0.9,
                        List.of()
                );
        var classificationService = new TaskClassificationService(
                client,
                new ClassificationPolicy(new ClassificationProperties())
        );
        VisitDecisionService visitDecisionService = mock(VisitDecisionService.class);
        when(visitDecisionService.decide(TaskTypeCode.PASSBOOK_REISSUE)).thenReturn(
                new VisitDecisionResponse(
                        TaskTypeCode.PASSBOOK_REISSUE,
                        "통장 재발급",
                        VisitDecision.VISIT_REQUIRED,
                        "본인 확인이 필요합니다.",
                        List.of(),
                        List.of()
                )
        );
        return new TaskClassificationWorkflow(classificationService, store, visitDecisionService);
    }

    private static class RecordingStore implements ClassificationResultStore {
        private final UUID id;
        private int saveCalls;
        private int suspendedSaveCalls;
        private long userId;
        private ClassificationResult result;

        private RecordingStore(UUID id) {
            this.id = id;
        }

        @Override
        public UUID save(long userId, ClassificationCommand command, ClassificationResult result) {
            saveCalls++;
            this.userId = userId;
            this.result = result;
            return id;
        }

        @Override
        public UUID saveSuspended(
                long userId,
                ClassificationCommand command,
                ClassificationResult pendingResult
        ) {
            suspendedSaveCalls++;
            this.userId = userId;
            this.result = pendingResult;
            return id;
        }

        @Override
        public SelectionOutcome confirmCandidate(
                long userId,
                UUID consultationId,
                TaskTypeCode selectedTask
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
