package com.piggyback.backend.domain.branch.service;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.branch.dto.NearbyBranchResponse;
import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.domain.LocatedBranch;
import com.piggyback.backend.recommendation.domain.LocationQuery;
import com.piggyback.backend.recommendation.service.BranchLocator;
import com.piggyback.backend.repository.BranchRepository;
import com.piggyback.backend.repository.BranchTaskRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상담과 무관하게 현재 위치(또는 법정동코드) 기준으로 가까운 지점을 조회한다.
 * 추천 이력을 남기지 않으며 GPS 조회 시 거리 오름차순으로 정렬한다.
 */
@Service
@RequiredArgsConstructor
public class NearbyBranchService {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;

    private final BranchRepository branchRepository;
    private final BranchTaskRepository branchTaskRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final BranchLocator branchLocator;
    private final RecommendationProperties properties;

    @Transactional(readOnly = true)
    public NearbyBranchResponse findNearby(
            TaskTypeCode taskTypeCode,
            Double lat,
            Double lng,
            String regionCode,
            Integer limit
    ) {
        LocationQuery location = LocationQuery.of(lat, lng, regionCode);
        validateLimit(limit);

        List<Branch> branches = taskTypeCode == null
                ? branchRepository.findAll()
                : findBranchesHandling(taskTypeCode);
        List<NearbyBranchResponse.NearbyBranchItem> items = branchLocator.locate(branches, location).stream()
                .limit(limit == null ? DEFAULT_LIMIT : limit)
                .map(this::toItem)
                .toList();

        return new NearbyBranchResponse(items, properties.getWalkingSpeedKmh());
    }

    private void validateLimit(Integer limit) {
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
    }

    private List<Branch> findBranchesHandling(TaskTypeCode taskTypeCode) {
        if (!taskTypeRepository.existsById(taskTypeCode)) {
            throw new TaskTypeNotFoundException();
        }
        return branchTaskRepository.findBranchesByTaskTypeCode(taskTypeCode);
    }

    private NearbyBranchResponse.NearbyBranchItem toItem(LocatedBranch located) {
        Branch branch = located.branch();
        Double distanceKm = located.hasDistance() ? roundOneDecimal(located.distanceKm()) : null;
        Integer walkMinutes = located.hasDistance()
                ? (int) Math.round(branchLocator.walkMinutes(located.distanceKm()))
                : null;
        return new NearbyBranchResponse.NearbyBranchItem(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getLat().doubleValue(),
                branch.getLng().doubleValue(),
                distanceKm,
                walkMinutes
        );
    }

    private double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
