package com.piggyback.backend.domain.branch.dto;

import java.util.List;

public record NearbyBranchResponse(
        List<NearbyBranchItem> branches,
        double walkingSpeedKmh
) {

    /** regionCode 조회처럼 거리를 알 수 없으면 distanceKm과 walkMinutes는 null이다. */
    public record NearbyBranchItem(
            Long branchId,
            String name,
            String address,
            String phone,
            Double lat,
            Double lng,
            Double distanceKm,
            Integer walkMinutes
    ) {
    }
}
