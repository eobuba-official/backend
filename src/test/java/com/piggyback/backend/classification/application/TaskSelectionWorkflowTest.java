package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskSelectionWorkflowTest {

    private final ClassificationResultStore store = mock(ClassificationResultStore.class);
    private final VisitDecisionService visitDecisionService = mock(VisitDecisionService.class);
    private final TaskSelectionWorkflow workflow = new TaskSelectionWorkflow(store, visitDecisionService);

    @Test
    void confirmsStoredCandidateAndDecidesVisitWithoutCallingLlm() {
        UUID consultationId = UUID.randomUUID();
        when(store.confirmCandidate(7L, consultationId, TaskTypeCode.DEPOSIT_EARLY_CLOSE))
                .thenReturn(ClassificationResultStore.SelectionOutcome.CONFIRMED);
        when(visitDecisionService.decide(TaskTypeCode.DEPOSIT_EARLY_CLOSE)).thenReturn(
                new VisitDecisionResponse(
                        TaskTypeCode.DEPOSIT_EARLY_CLOSE,
                        "예금 중도해지",
                        VisitDecision.CHECK_NEEDED,
                        "상품에 따라 먼저 확인해 보세요.",
                        List.of(),
                        List.of()
                )
        );

        var result = workflow.select(7L, consultationId, "deposit_early_close");

        assertEquals(consultationId, result.consultationId());
        assertEquals(TaskTypeCode.DEPOSIT_EARLY_CLOSE, result.task().taskTypeCode());
        verify(store).confirmCandidate(7L, consultationId, TaskTypeCode.DEPOSIT_EARLY_CLOSE);
        verify(visitDecisionService).decide(TaskTypeCode.DEPOSIT_EARLY_CLOSE);
    }

    @Test
    void mapsStoreFailuresToPublicErrorCodes() {
        Map<ClassificationResultStore.SelectionOutcome, ErrorCode> expectedErrors = Map.of(
                ClassificationResultStore.SelectionOutcome.CONSULTATION_NOT_FOUND,
                ErrorCode.CONSULTATION_NOT_FOUND,
                ClassificationResultStore.SelectionOutcome.INVALID_STATE,
                ErrorCode.INVALID_STATE,
                ClassificationResultStore.SelectionOutcome.TASK_NOT_CANDIDATE,
                ErrorCode.TASK_TYPE_NOT_FOUND
        );

        expectedErrors.forEach((outcome, errorCode) -> {
            when(store.confirmCandidate(anyLong(), any(), any())).thenReturn(outcome);
            var exception = assertThrows(
                    BusinessException.class,
                    () -> workflow.select(7L, UUID.randomUUID(), "ACCOUNT_TRANSFER")
            );
            assertEquals(errorCode, exception.getErrorCode());
            reset(store);
        });
    }

    @Test
    void rejectsUnknownTaskCodeBeforeAccessingStore() {
        var exception = assertThrows(
                BusinessException.class,
                () -> workflow.select(7L, UUID.randomUUID(), "DEPOSIT_MATURITY")
        );

        assertEquals(ErrorCode.TASK_TYPE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(store);
    }
}
