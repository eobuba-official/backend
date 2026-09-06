package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.TaskSelectionService;
import com.piggyback.backend.classification.application.TaskSelectionWorkflow;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.domain.OfficialChannel;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import com.piggyback.backend.visit.service.VisitDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskSelectionControllerTest {

    @Test
    void returnsConfirmedTaskAndVisitDecisionUsingTheV12Contract() throws Exception {
        ClassificationResultStore store = new FixedStore(ClassificationResultStore.SelectionOutcome.CONFIRMED);
        VisitDecisionService visitDecisionService = mock(VisitDecisionService.class);
        when(visitDecisionService.decide(TaskTypeCode.DEPOSIT_EARLY_CLOSE)).thenReturn(
                new VisitDecisionResponse(
                TaskTypeCode.DEPOSIT_EARLY_CLOSE,
                "예금 중도해지",
                VisitDecision.CHECK_NEEDED,
                "상품에 따라 앱에서 해지가 가능할 수 있어요. 먼저 확인해 보세요.",
                List.of(),
                List.of(new OfficialChannel(
                        "KB국민은행 고객센터",
                        "1588-9999",
                        "해지 가능 여부를 전화로 확인"
                ))
        ));
        MockMvc mockMvc = mockMvc(store, visitDecisionService);
        UUID consultationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/consultations/{id}/task-selection", consultationId)
                        .requestAttr("authenticatedUserId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTypeCode\":\"DEPOSIT_EARLY_CLOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consultationId").value(consultationId.toString()))
                .andExpect(jsonPath("$.data.status").value("TASK_CONFIRMED"))
                .andExpect(jsonPath("$.data.task.taskTypeCode").value("DEPOSIT_EARLY_CLOSE"))
                .andExpect(jsonPath("$.data.visitDecision.decision").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.visitDecision.officialChannels[0].phone").value("1588-9999"));
    }

    @Test
    void returnsContractErrorForASelectionOutsideTheCandidates() throws Exception {
        ClassificationResultStore store = new FixedStore(
                ClassificationResultStore.SelectionOutcome.TASK_NOT_CANDIDATE
        );
        MockMvc mockMvc = mockMvc(store, mock(VisitDecisionService.class));

        mockMvc.perform(post("/api/v1/consultations/{id}/task-selection", UUID.randomUUID())
                        .requestAttr("authenticatedUserId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTypeCode\":\"ACCOUNT_TRANSFER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TASK_TYPE_NOT_FOUND"));
    }

    @Test
    void returnsCommonInvalidInputErrorWhenTaskCodeIsBlank() throws Exception {
        MockMvc mockMvc = mockMvc(
                new FixedStore(ClassificationResultStore.SelectionOutcome.CONFIRMED),
                mock(VisitDecisionService.class)
        );

        mockMvc.perform(post("/api/v1/consultations/{id}/task-selection", UUID.randomUUID())
                        .requestAttr("authenticatedUserId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTypeCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsSelectionWithoutAuthenticatedUserContext() throws Exception {
        MockMvc mockMvc = mockMvc(
                new FixedStore(ClassificationResultStore.SelectionOutcome.CONFIRMED),
                mock(VisitDecisionService.class)
        );

        mockMvc.perform(post("/api/v1/consultations/{id}/task-selection", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskTypeCode\":\"ACCOUNT_TRANSFER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private MockMvc mockMvc(
            ClassificationResultStore store,
            VisitDecisionService visitDecisionService
    ) {
        var selectionService = new TaskSelectionService(store);
        var workflow = new TaskSelectionWorkflow(selectionService, visitDecisionService);
        return MockMvcBuilders
                .standaloneSetup(new TaskSelectionController(workflow))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private record FixedStore(SelectionOutcome outcome) implements ClassificationResultStore {
        @Override
        public UUID save(
                long userId,
                com.piggyback.backend.classification.application.ClassificationCommand command,
                ClassificationResult result
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID saveSuspended(
                long userId,
                com.piggyback.backend.classification.application.ClassificationCommand command,
                ClassificationResult pendingResult,
                java.util.List<com.piggyback.backend.classification.domain.ValidatedFraudPattern> fraudPatterns
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SelectionOutcome confirmCandidate(
                long userId,
                UUID consultationId,
                TaskTypeCode selectedTask
        ) {
            return outcome;
        }
    }
}
