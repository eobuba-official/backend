package com.piggyback.backend.recommendation.dto;

import java.util.List;

public record BranchRecommendationResponse(
        List<RecommendationItemResponse> recommendations,
        RecommendationWeightsResponse weights
) {
}
