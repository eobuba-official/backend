package com.piggyback.backend.recommendation.service;

import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.domain.LocatedBranch;
import com.piggyback.backend.recommendation.domain.LocationQuery;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지점 목록에 위치 조건(반경 또는 법정동코드)을 적용하고 거리·도보 시간을 계산한다.
 * 지점 추천과 가까운 지점 조회가 같은 기준을 쓰도록 여기서만 처리한다.
 */
@Component
@RequiredArgsConstructor
public class BranchLocator {

    private static final double MINUTES_PER_HOUR = 60.0;

    private final DistanceCalculator distanceCalculator;
    private final RecommendationProperties properties;

    public List<LocatedBranch> locate(Collection<Branch> branches, LocationQuery query) {
        if (query.isGpsBased()) {
            return branches.stream()
                    .map(branch -> new LocatedBranch(branch, distanceCalculator.calculateKm(
                            query.lat(),
                            query.lng(),
                            branch.getLat().doubleValue(),
                            branch.getLng().doubleValue()
                    )))
                    .filter(located -> located.distanceKm() <= properties.getSearchRadiusKm())
                    .sorted(Comparator
                            .comparingDouble(LocatedBranch::distanceKm)
                            .thenComparing(located -> located.branch().getId()))
                    .toList();
        }
        return branches.stream()
                .filter(branch -> branch.getRegionCode().equals(query.regionCode()))
                .map(branch -> new LocatedBranch(branch, null))
                .sorted(Comparator
                        .comparing((LocatedBranch located) -> located.branch().getName())
                        .thenComparing(located -> located.branch().getId()))
                .toList();
    }

    /** 거리(km)를 설정된 보행 속도로 걸었을 때 걸리는 시간(분). 반올림하지 않은 값이다. */
    public double walkMinutes(double distanceKm) {
        return distanceKm / properties.getWalkingSpeedKmh() * MINUTES_PER_HOUR;
    }
}
