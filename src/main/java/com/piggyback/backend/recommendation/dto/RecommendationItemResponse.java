package com.piggyback.backend.recommendation.dto;

import com.piggyback.backend.recommendation.domain.CongestionSource;

public record RecommendationItemResponse(
        int rank,
        BranchResponse branch,
        VisitTimeResponse visitTime,
        int expectedWaitMinutes,
        CongestionSource congestionSource,
        Integer walkMinutes,
        int totalMinutes,
        String sentence
) {
}
