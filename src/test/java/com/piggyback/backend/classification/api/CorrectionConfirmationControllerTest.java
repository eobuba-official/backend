package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.AnalyzeResult;
import com.piggyback.backend.classification.application.CorrectionConfirmationWorkflow;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrectionConfirmationControllerTest {

    private CorrectionConfirmationWorkflow workflow;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workflow = mock(CorrectionConfirmationWorkflow.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CorrectionConfirmationController(workflow))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void confirmsCorrectedUtteranceAndSelectedTask() throws Exception {
        UUID consultationId = UUID.randomUUID();
        String utterance = "통장을 잃어버려서 다시 만들고 싶어";
        var classification = ClassificationResult.confirmed(
                "잘못 인식된 원문",
                utterance,
                0.4,
                TaskTypeCode.PASSBOOK_REISSUE,
                false
        );
        var visitDecision = new VisitDecisionView(
                VisitDecision.VISIT_REQUIRED,
                "통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다.",
                List.of(),
                List.of()
        );
        when(workflow.confirm(eq(7L), eq(consultationId), eq(utterance), eq("PASSBOOK_REISSUE")))
                .thenReturn(AnalyzeResult.normal(consultationId, classification, visitDecision));

        mockMvc.perform(post(
                                "/api/v1/consultations/{id}/correction-confirmation",
                                consultationId
                        )
                        .requestAttr("authenticatedUserId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmedUtterance": "통장을 잃어버려서 다시 만들고 싶어",
                                  "taskTypeCode": "PASSBOOK_REISSUE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationId").value(consultationId.toString()))
                .andExpect(jsonPath("$.data.status").value("TASK_CONFIRMED"))
                .andExpect(jsonPath("$.data.classification.task.taskTypeCode")
                        .value("PASSBOOK_REISSUE"))
                .andExpect(jsonPath("$.data.visitDecision.decision").value("VISIT_REQUIRED"))
                .andExpect(jsonPath("$.data.visitDecision.reason")
                        .value("통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다."));
    }

    @Test
    void validatesConfirmedUtterance() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/consultations/{id}/correction-confirmation",
                                UUID.randomUUID()
                        )
                        .requestAttr("authenticatedUserId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedUtterance\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/consultations/{id}/correction-confirmation",
                                UUID.randomUUID()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedUtterance\":\"통장을 다시 만들고 싶어\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
