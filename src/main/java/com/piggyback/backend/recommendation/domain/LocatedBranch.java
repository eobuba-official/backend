package com.piggyback.backend.recommendation.domain;

import com.piggyback.backend.entity.Branch;

/**
 * 위치 조건을 통과한 지점. regionCode 기준 조회면 거리를 알 수 없어 distanceKm이 null이다.
 */
public record LocatedBranch(Branch branch, Double distanceKm) {

    public boolean hasDistance() {
        return distanceKm != null;
    }
}
