package com.piggyback.backend.recommendation.controller;

import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.service.BranchRecommendationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/branches")
public class BranchRecommendationController {

    private final BranchRecommendationService branchRecommendationService;

    @GetMapping("/recommendations")
    public ApiResponse<BranchRecommendationResponse> getRecommendations(
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam UUID consultationId,
            @RequestParam TaskTypeCode taskTypeCode,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(branchRecommendationService.recommend(
                userId,
                consultationId,
                taskTypeCode,
                lat,
                lng,
                regionCode,
                limit
        ));
    }
}
