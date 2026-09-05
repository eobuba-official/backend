package com.piggyback.backend.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RecommendationWeightsResponse(
        @JsonProperty("wait") double waiting,
        double distance
) {
}
