package com.piggyback.backend.recommendation.dto;

public record BranchResponse(
        Long branchId,
        String name,
        String address,
        String phone,
        Double lat,
        Double lng,
        Double distanceKm
) {
}
