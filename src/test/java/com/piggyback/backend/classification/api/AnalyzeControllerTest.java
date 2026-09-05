package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.AnalyzeResult;
import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.application.TaskClassificationWorkflow;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.classification.infrastructure.llm.LlmClassificationException;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.domain.TaskTypeCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyzeControllerTest {

    private TaskClassificationWorkflow workflow;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workflow = mock(TaskClassificationWorkflow.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyzeController(workflow))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsConfirmedClassificationUsingTheV12Contract() throws Exception {
        UUID consultationId = UUID.randomUUID();
        var classification = ClassificationResult.confirmed(
                "통장을 잃어버렸는데 다시 만들고 싶어",
                0.93,
                TaskTypeCode.PASSBOOK_REISSUE,
                true
        );
        var visitDecision = new VisitDecisionView(
                "VISIT_REQUIRED",
                "본인 확인이 필요합니다.",
                List.of(),
                List.of()
        );
        when(workflow.analyze(eq(7L), any(ClassificationCommand.class)))
                .thenReturn(AnalyzeResult.normal(consultationId, classification, visitDecision));

        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "utterance": "내가 동정을 잃어버렸는데 다시 만들고 싶어",
                                  "inputMethod": "VOICE",
                                  "sttConfidence": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consultationId").value(consultationId.toString()))
                .andExpect(jsonPath("$.data.status").value("TASK_CONFIRMED"))
                .andExpect(jsonPath("$.data.classification.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.classification.correctedUtterance")
                        .value("통장을 잃어버렸는데 다시 만들고 싶어"))
                .andExpect(jsonPath("$.data.classification.task.taskTypeCode")
                        .value("PASSBOOK_REISSUE"))
                .andExpect(jsonPath("$.data.classification.sttRecheckNeeded").value(true))
                .andExpect(jsonPath("$.data.visitDecision.decision").value("VISIT_REQUIRED"));
    }

    @Test
    void mapsLlmFailureToTheCommon502Contract() throws Exception {
        when(workflow.analyze(eq(7L), any(ClassificationCommand.class)))
                .thenThrow(new LlmClassificationException("LLM request failed"));

        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\"잔액 알려줘\",\"inputMethod\":\"TEXT\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LLM_ERROR"));
    }

    @Test
    void returnsTwoToThreeCandidatesWithoutVisitDecision() throws Exception {
        UUID consultationId = UUID.randomUUID();
        var classification = ClassificationResult.candidates(
                "돈 관련 업무를 하고 싶어",
                0.48,
                List.of(
                        TaskTypeView.from(TaskTypeCode.DEPOSIT_EARLY_CLOSE),
                        TaskTypeView.from(TaskTypeCode.AUTO_TRANSFER_CHANGE)
                ),
                false
        );
        when(workflow.analyze(eq(7L), any(ClassificationCommand.class)))
                .thenReturn(AnalyzeResult.normal(consultationId, classification, null));

        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\"돈 관련 업무를 하고 싶어\",\"inputMethod\":\"TEXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANDIDATES_SUGGESTED"))
                .andExpect(jsonPath("$.data.classification.status").value("CANDIDATES"))
                .andExpect(jsonPath("$.data.classification.candidates.length()").value(2))
                .andExpect(jsonPath("$.data.visitDecision").isEmpty());
    }

    @Test
    void returnsGuidanceWhenClassificationIsUnavailable() throws Exception {
        UUID consultationId = UUID.randomUUID();
        var classification = ClassificationResult.unclassified("그거 해줘", 0.2, false);
        when(workflow.analyze(eq(7L), any(ClassificationCommand.class)))
                .thenReturn(AnalyzeResult.normal(consultationId, classification, null));

        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\"그거 해줘\",\"inputMethod\":\"TEXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNCLASSIFIED"))
                .andExpect(jsonPath("$.data.guidance")
                        .value(ClassificationResult.UNCLASSIFIED_GUIDANCE));
    }

    @Test
    void hidesPendingClassificationWhenFraudIsDetected() throws Exception {
        UUID consultationId = UUID.randomUUID();
        var pending = ClassificationResult.confirmed(
                "안전계좌로 돈을 보내래",
                0.9,
                TaskTypeCode.ACCOUNT_TRANSFER,
                true
        );
        var suspended = AnalyzeResult.suspended(
                consultationId,
                pending,
                List.of(new LlmFraudPattern(
                        "SAFE_ACCOUNT",
                        "안전계좌",
                        "은행은 안전계좌 송금을 요구하지 않습니다."
                ))
        );
        when(workflow.analyze(eq(7L), any(ClassificationCommand.class))).thenReturn(suspended);

        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\"안전계좌로 돈을 보내래\",\"inputMethod\":\"VOICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FRAUD_WARNING"))
                .andExpect(jsonPath("$.data.classification.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.classification.confidence").isEmpty())
                .andExpect(jsonPath("$.data.classification.task").isEmpty())
                .andExpect(jsonPath("$.data.classification.candidates.length()").value(0))
                .andExpect(jsonPath("$.data.fraudCheck.patterns[0].label").value("안전계좌 요구"))
                .andExpect(jsonPath("$.data.fraudCheck.safetyActions.length()").value(4));
    }

    @Test
    void validatesAnalyzeRequestWithTheCommonErrorContract() throws Exception {
        mockMvc.perform(post("/api/v1/analyze")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\" \",\"inputMethod\":\"VOICE\",\"sttConfidence\":1.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsRequestWithoutAuthenticatedUserContext() throws Exception {
        mockMvc.perform(post("/api/v1/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utterance\":\"잔액 알려줘\",\"inputMethod\":\"TEXT\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
