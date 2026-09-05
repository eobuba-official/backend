package com.piggyback.backend.recommendation.controller;

import static com.piggyback.backend.common.auth.JwtAuthFilter.USER_ID_ATTRIBUTE;
import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.recommendation.domain.CongestionSource;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.dto.BranchResponse;
import com.piggyback.backend.recommendation.dto.RecommendationItemResponse;
import com.piggyback.backend.recommendation.dto.RecommendationWeightsResponse;
import com.piggyback.backend.recommendation.dto.VisitTimeResponse;
import com.piggyback.backend.recommendation.service.BranchRecommendationService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BranchRecommendationControllerTest {

    private static final UUID CONSULTATION_ID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");

    private BranchRecommendationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(BranchRecommendationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BranchRecommendationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsRecommendationsWrappedInApiResponse() throws Exception {
        RecommendationItemResponse item = new RecommendationItemResponse(
                1,
                new BranchResponse(103L, "KB국민은행 종로지점", "서울 종로구 종로 1", "02-000-0000", 0.5),
                new VisitTimeResponse(LocalDate.of(2026, 9, 4), "내일", "10:00-11:00", "오전 10시"),
                5,
                CongestionSource.MOCK,
                91.5,
                "내일 오전 10시에 방문을 추천해요."
        );
        when(service.recommend(
                eq(1L),
                eq(CONSULTATION_ID),
                eq(PASSBOOK_REISSUE),
                eq(37.5665),
                eq(126.9780),
                eq(null),
                eq(null)
        )).thenReturn(new BranchRecommendationResponse(
                List.of(item),
                new RecommendationWeightsResponse(0.6, 0.4)
        ));

        mockMvc.perform(get("/api/v1/branches/recommendations")
                        .requestAttr(USER_ID_ATTRIBUTE, 1L)
                        .param("consultationId", CONSULTATION_ID.toString())
                        .param("taskTypeCode", "PASSBOOK_REISSUE")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendations[0].rank").value(1))
                .andExpect(jsonPath("$.data.recommendations[0].branch.branchId").value(103))
                .andExpect(jsonPath("$.data.recommendations[0].visitTime.dayLabel").value("내일"))
                .andExpect(jsonPath("$.data.recommendations[0].expectedWaitMinutes").value(5))
                .andExpect(jsonPath("$.data.recommendations[0].congestionSource").value("MOCK"))
                .andExpect(jsonPath("$.data.weights.wait").value(0.6))
                .andExpect(jsonPath("$.data.weights.distance").value(0.4));
    }

    @Test
    void returnsInvalidInputForUnknownTaskTypeCode() throws Exception {
        mockMvc.perform(get("/api/v1/branches/recommendations")
                        .requestAttr(USER_ID_ATTRIBUTE, 1L)
                        .param("consultationId", CONSULTATION_ID.toString())
                        .param("taskTypeCode", "UNKNOWN")
                        .param("regionCode", "1111013500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void returnsInvalidInputForMalformedConsultationId() throws Exception {
        mockMvc.perform(get("/api/v1/branches/recommendations")
                        .requestAttr(USER_ID_ATTRIBUTE, 1L)
                        .param("consultationId", "not-a-uuid")
                        .param("taskTypeCode", "PASSBOOK_REISSUE")
                        .param("regionCode", "1111013500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
