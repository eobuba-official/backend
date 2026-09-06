package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskClassificationWorkflowTest {

    @Test
    void classifiesAndStoresANonFraudConsultation() {
        UUID consultationId = UUID.randomUUID();
        var store = new RecordingStore(consultationId);
        var fixture = workflow(false, List.of(), store);
        var command = new ClassificationCommand("통장을 잃어버렸어", InputMethod.VOICE, null);

        var outcome = fixture.workflow().analyze(7L, command);

        assertEquals(consultationId, outcome.consultationId());
        assertEquals("TASK_CONFIRMED", outcome.status());
        assertEquals(VisitDecision.VISIT_REQUIRED, outcome.visitDecision().decision());
        assertEquals(7L, store.userId);
        assertEquals(TaskTypeCode.PASSBOOK_REISSUE, store.result.task().taskTypeCode());
    }

    @Test
    void storesOnlyValidatedFraudPatternsAndSkipsVisitDecision() {
        var store = new RecordingStore(UUID.randomUUID());
        var fixture = workflow(
                true,
                List.of(
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
                ),
                store
        );

        var outcome = fixture.workflow().analyze(
                7L,
                new ClassificationCommand("안전계좌로 보내래", InputMethod.TEXT, null)
        );

        assertEquals("FRAUD_WARNING", outcome.status());
        assertEquals("SUSPENDED", outcome.classification().status());
        assertEquals(null, outcome.visitDecision());
        assertEquals(1, outcome.fraudCheck().patterns().size());
        assertEquals("안전계좌 요구", outcome.fraudCheck().patterns().get(0).label());
        assertEquals(4, outcome.fraudCheck().safetyActions().size());
        assertEquals(1, store.suspendedSaveCalls);
        assertEquals(0, store.saveCalls);
        assertEquals(1, store.fraudPatterns.size());
        verify(fixture.visitDecisionService(), never()).decide(TaskTypeCode.PASSBOOK_REISSUE);
    }

    @Test
    void validPatternTakesPriorityEvenWhenLlmDetectedFlagIsFalse() {
        var store = new RecordingStore(UUID.randomUUID());
        var fixture = workflow(
                false,
                List.of(new LlmFraudPattern(
                        "SECRECY",
                        "가족에게 말하지 마",
                        "비밀 유지를 요구했습니다."
                )),
                store
        );

        var outcome = fixture.workflow().analyze(
                7L,
                new ClassificationCommand("가족에게 말하지 마", InputMethod.TEXT, null)
        );

        assertEquals("FRAUD_WARNING", outcome.status());
        assertEquals(1, store.suspendedSaveCalls);
        assertEquals(0, store.saveCalls);
    }

    @Test
    void invalidPatternsDoNotTriggerWarningEvenWhenLlmDetectedFlagIsTrue() {
        var store = new RecordingStore(UUID.randomUUID());
        var fixture = workflow(
                true,
                List.of(new LlmFraudPattern(
                        "URGENCY",
                        "지금 당장",
                        "발화에 없는 근거입니다."
                )),
                store
        );

        var outcome = fixture.workflow().analyze(
                7L,
                new ClassificationCommand("통장을 잃어버렸어", InputMethod.TEXT, null)
        );

        assertEquals("TASK_CONFIRMED", outcome.status());
        assertEquals(0, store.suspendedSaveCalls);
        assertEquals(1, store.saveCalls);
    }

    private WorkflowFixture workflow(
            boolean fraudDetected,
            List<LlmFraudPattern> fraudPatterns,
            RecordingStore store
    ) {
        var client = (com.piggyback.backend.classification.port.TaskClassificationClient) utterance ->
                new LlmAnalysisOutput(
                        "test-model",
                        "test-prompt",
                        utterance,
                        fraudDetected,
                        fraudPatterns,
                        "PASSBOOK_REISSUE",
                        0.9,
                        List.of()
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
        return new WorkflowFixture(
                new TaskClassificationWorkflow(
                        client,
                        new ClassificationPolicy(new ClassificationProperties()),
                        new FraudDetectionPolicy(),
                        store,
                        visitDecisionService
                ),
                visitDecisionService
        );
    }

    private record WorkflowFixture(
            TaskClassificationWorkflow workflow,
            VisitDecisionService visitDecisionService
    ) {
    }

    private static class RecordingStore implements ClassificationResultStore {
        private final UUID id;
        private int saveCalls;
        private int suspendedSaveCalls;
        private long userId;
        private ClassificationResult result;
        private List<ValidatedFraudPattern> fraudPatterns = List.of();

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
                ClassificationResult pendingResult,
                List<ValidatedFraudPattern> fraudPatterns
        ) {
            suspendedSaveCalls++;
            this.userId = userId;
            this.result = pendingResult;
            this.fraudPatterns = List.copyOf(fraudPatterns);
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
