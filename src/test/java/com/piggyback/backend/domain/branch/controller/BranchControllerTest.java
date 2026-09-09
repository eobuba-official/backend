package com.piggyback.backend.domain.branch.controller;

import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.domain.branch.dto.NearbyBranchResponse;
import com.piggyback.backend.domain.branch.service.NearbyBranchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BranchControllerTest {

    private NearbyBranchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(NearbyBranchService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BranchController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNearbyBranchesWithoutConsultationId() throws Exception {
        NearbyBranchResponse.NearbyBranchItem item = new NearbyBranchResponse.NearbyBranchItem(
                103L, "KB국민은행 종로지점", "서울 종로구 종로 1", "02-000-0000", 37.57, 126.982, 0.5, 8
        );
        when(service.findNearby(eq(null), eq(37.5665), eq(126.9780), eq(null), eq(null)))
                .thenReturn(new NearbyBranchResponse(List.of(item), 4.0));

        mockMvc.perform(get("/api/v1/branches/nearby")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branches[0].branchId").value(103))
                .andExpect(jsonPath("$.data.branches[0].name").value("KB국민은행 종로지점"))
                .andExpect(jsonPath("$.data.branches[0].lat").value(37.57))
                .andExpect(jsonPath("$.data.branches[0].distanceKm").value(0.5))
                .andExpect(jsonPath("$.data.branches[0].walkMinutes").value(8))
                .andExpect(jsonPath("$.data.walkingSpeedKmh").value(4.0));
    }

    @Test
    void passesTaskTypeAndLimitToService() throws Exception {
        when(service.findNearby(eq(PASSBOOK_REISSUE), eq(null), eq(null), eq("1111013500"), eq(3)))
                .thenReturn(new NearbyBranchResponse(List.of(), 4.0));

        mockMvc.perform(get("/api/v1/branches/nearby")
                        .param("regionCode", "1111013500")
                        .param("taskTypeCode", "PASSBOOK_REISSUE")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branches").isEmpty());
    }

    @Test
    void returnsInvalidInputForUnknownTaskTypeCode() throws Exception {
        mockMvc.perform(get("/api/v1/branches/nearby")
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("taskTypeCode", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void returnsInvalidInputWhenLocationIsMissing() throws Exception {
        when(service.findNearby(eq(null), eq(null), eq(null), eq(null), eq(null)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "lat/lng 또는 regionCode 중 하나는 필수입니다."));

        mockMvc.perform(get("/api/v1/branches/nearby"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("lat/lng 또는 regionCode 중 하나는 필수입니다."));
    }
}
