package com.piggyback.backend.checklist.controller;

import static com.piggyback.backend.checklist.domain.ChecklistConditionCode.IS_PROXY;
import static com.piggyback.backend.checklist.domain.ChecklistItemStatus.INCLUDED;
import static com.piggyback.backend.common.auth.JwtAuthFilter.USER_ID_ATTRIBUTE;
import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piggyback.backend.checklist.dto.ChecklistAnswerRequest;
import com.piggyback.backend.checklist.dto.ChecklistAnswerResponse;
import com.piggyback.backend.checklist.dto.ChecklistQuestionResponse;
import com.piggyback.backend.checklist.dto.ChecklistQuestionsResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistItemResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistResponse;
import com.piggyback.backend.checklist.service.ConsultationChecklistService;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConsultationChecklistControllerTest {

    private static final Long USER_ID = 1L;
    private static final UUID CONSULTATION_ID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");

    private ConsultationChecklistService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ConsultationChecklistService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultationChecklistController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsQuestionsWithCurrentAnswer() throws Exception {
        when(service.getQuestions(USER_ID, CONSULTATION_ID)).thenReturn(
                new ChecklistQuestionsResponse(List.of(new ChecklistQuestionResponse(
                        IS_PROXY,
                        "다른 사람이 대신 방문하나요?",
                        "BOOLEAN",
                        true,
                        true
                )))
        );

        mockMvc.perform(get(path("/questions")).requestAttr(USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].conditionCode").value("IS_PROXY"))
                .andExpect(jsonPath("$.data.questions[0].answered").value(true))
                .andExpect(jsonPath("$.data.questions[0].answer").value(true));
    }

    @Test
    void savesAnswers() throws Exception {
        when(service.saveAnswers(eq(USER_ID), eq(CONSULTATION_ID), any(ChecklistAnswerRequest.class)))
                .thenReturn(new ChecklistAnswerResponse(1));

        mockMvc.perform(put(path("/answers"))
                        .requestAttr(USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"conditionCode":"IS_PROXY","value":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedCount").value(1));
    }

    @Test
    void returnsResolvedChecklist() throws Exception {
        when(service.getResolvedChecklist(USER_ID, CONSULTATION_ID)).thenReturn(
                new ResolvedChecklistResponse(
                        PASSBOOK_REISSUE,
                        "통장 재발급",
                        true,
                        List.of(new ResolvedChecklistItemResponse(
                                "ID_CARD",
                                "신분증",
                                "주민등록증이나 운전면허증",
                                true,
                                INCLUDED,
                                "항상 필요한 준비물이에요.",
                                1
                        ))
                )
        );

        mockMvc.perform(get(path("")).requestAttr(USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("INCLUDED"));
    }

    @Test
    void rejectsEmptyAnswers() throws Exception {
        mockMvc.perform(put(path("/answers"))
                        .requestAttr(USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    private String path(String suffix) {
        return "/api/v1/consultations/" + CONSULTATION_ID + "/checklist" + suffix;
    }
}
