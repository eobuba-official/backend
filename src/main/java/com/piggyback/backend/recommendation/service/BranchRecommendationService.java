package com.piggyback.backend.recommendation.service;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.entity.CongestionSlot;
import com.piggyback.backend.entity.Recommendation;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.domain.CongestionSource;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.dto.BranchResponse;
import com.piggyback.backend.recommendation.dto.RecommendationItemResponse;
import com.piggyback.backend.recommendation.dto.RecommendationWeightsResponse;
import com.piggyback.backend.recommendation.dto.VisitTimeResponse;
import com.piggyback.backend.recommendation.exception.ConsultationNotFoundException;
import com.piggyback.backend.recommendation.repository.ConsultationReferenceRepository;
import com.piggyback.backend.repository.BranchTaskRepository;
import com.piggyback.backend.repository.CongestionSlotRepository;
import com.piggyback.backend.repository.RecommendationRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchRecommendationService {

    private static final int MAX_RESULT_LIMIT = 5;
    private static final List<String> BUSINESS_TIME_SLOTS = List.of(
            "09:00-10:00",
            "10:00-11:00",
            "11:00-12:00",
            "12:00-13:00",
            "13:00-14:00",
            "14:00-15:00",
            "15:00-16:00"
    );

    private final TaskTypeRepository taskTypeRepository;
    private final BranchTaskRepository branchTaskRepository;
    private final CongestionSlotRepository congestionSlotRepository;
    private final RecommendationRepository recommendationRepository;
    private final ConsultationReferenceRepository consultationReferenceRepository;
    private final RecommendationProperties properties;
    private final DistanceCalculator distanceCalculator;
    private final Clock clock;

    public BranchRecommendationResponse recommend(
            Long userId,
            UUID consultationId,
            TaskTypeCode taskTypeCode,
            Double lat,
            Double lng,
            String regionCode,
            Integer limit
    ) {
        validateRequest(lat, lng, regionCode, limit);
        validateReferences(userId, consultationId, taskTypeCode);

        boolean gpsBased = lat != null;
        int resultLimit = limit == null ? properties.getResultLimit() : limit;
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDate> visitDates = businessDates(now.toLocalDate(), properties.getPlanningBusinessDays());

        List<BranchDistance> branches = findAvailableBranches(taskTypeCode, lat, lng, regionCode, gpsBased);
        List<RecommendationCandidate> candidates = createCandidates(branches, visitDates, now, gpsBased);
        List<ScoredCandidate> selected = scoreAndSelect(candidates, resultLimit, gpsBased);

        recommendationRepository.deleteByConsultationId(consultationId.toString());
        List<RecommendationItemResponse> responses = toResponsesAndSave(consultationId, selected, now.toLocalDate(), gpsBased);

        return new BranchRecommendationResponse(
                responses,
                new RecommendationWeightsResponse(properties.getWaitingWeight(), properties.getDistanceWeight())
        );
    }

    private void validateRequest(Double lat, Double lng, String regionCode, Integer limit) {
        boolean hasLat = lat != null;
        boolean hasLng = lng != null;
        boolean hasRegion = regionCode != null && !regionCode.isBlank();

        if (hasLat != hasLng) {
            throw invalidInput("lat과 lng는 함께 입력해야 합니다.");
        }
        if (!hasLat && !hasRegion) {
            throw invalidInput("lat/lng 또는 regionCode 중 하나는 필수입니다.");
        }
        if (hasLat && (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
            throw invalidInput("위도 또는 경도 범위가 올바르지 않습니다.");
        }
        if (!hasLat && !regionCode.matches("\\d{10}")) {
            throw invalidInput("regionCode는 숫자 10자리여야 합니다.");
        }
        if (limit != null && (limit < 1 || limit > MAX_RESULT_LIMIT)) {
            throw invalidInput("limit은 1 이상 5 이하여야 합니다.");
        }
    }

    private void validateReferences(Long userId, UUID consultationId, TaskTypeCode taskTypeCode) {
        if (!taskTypeRepository.existsById(taskTypeCode)) {
            throw new TaskTypeNotFoundException();
        }
        if (!consultationReferenceRepository.existsByIdAndUserId(consultationId, userId)) {
            throw new ConsultationNotFoundException();
        }
    }

    private List<BranchDistance> findAvailableBranches(
            TaskTypeCode taskTypeCode,
            Double lat,
            Double lng,
            String regionCode,
            boolean gpsBased
    ) {
        return branchTaskRepository.findBranchesByTaskTypeCode(taskTypeCode).stream()
                .filter(branch -> gpsBased || branch.getRegionCode().equals(regionCode))
                .map(branch -> new BranchDistance(
                        branch,
                        gpsBased ? distanceCalculator.calculateKm(
                                lat,
                                lng,
                                branch.getLat().doubleValue(),
                                branch.getLng().doubleValue()
                        ) : 0.0
                ))
                .filter(branch -> !gpsBased || branch.distanceKm() <= properties.getSearchRadiusKm())
                .toList();
    }

    private List<RecommendationCandidate> createCandidates(
            List<BranchDistance> branches,
            List<LocalDate> visitDates,
            LocalDateTime now,
            boolean gpsBased
    ) {
        if (branches.isEmpty()) {
            return List.of();
        }

        Set<Long> branchIds = branches.stream()
                .map(branch -> branch.branch().getId())
                .collect(Collectors.toSet());
        Set<Integer> daysOfWeek = visitDates.stream()
                .map(date -> date.getDayOfWeek().getValue())
                .collect(Collectors.toSet());
        Map<CongestionKey, CongestionSlot> congestionByKey = congestionSlotRepository
                .findForRecommendation(branchIds, daysOfWeek).stream()
                .collect(Collectors.toMap(
                        slot -> new CongestionKey(
                                slot.getBranch().getId(),
                                slot.getDayOfWeek(),
                                slot.getTimeSlot()
                        ),
                        slot -> slot
                ));

        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (BranchDistance branch : branches) {
            for (LocalDate date : visitDates) {
                for (String timeSlot : BUSINESS_TIME_SLOTS) {
                    if (isPastOrStarted(date, timeSlot, now)) {
                        continue;
                    }
                    CongestionSlot congestion = congestionByKey.get(
                            new CongestionKey(branch.branch().getId(), date.getDayOfWeek().getValue(), timeSlot)
                    );
                    int waitMinutes = congestion == null
                            ? properties.getDefaultWaitMinutes()
                            : congestion.getExpectedWaitMinutes();
                    CongestionSource source = congestion == null
                            ? CongestionSource.FALLBACK
                            : CongestionSource.MOCK;
                    candidates.add(new RecommendationCandidate(
                            branch.branch(),
                            date,
                            timeSlot,
                            waitMinutes,
                            source,
                            gpsBased ? branch.distanceKm() : 0.0
                    ));
                }
            }
        }
        return candidates;
    }

    private List<ScoredCandidate> scoreAndSelect(
            List<RecommendationCandidate> candidates,
            int limit,
            boolean gpsBased
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Range waitRange = range(candidates.stream().map(RecommendationCandidate::waitMinutes).toList());
        Range distanceRange = gpsBased
                ? range(candidates.stream().map(RecommendationCandidate::distanceKm).toList())
                : new Range(0, 0);

        return candidates.stream()
                .map(candidate -> {
                    double waitScore = inverseScore(candidate.waitMinutes(), waitRange);
                    double distanceScore = gpsBased
                            ? inverseScore(candidate.distanceKm(), distanceRange)
                            : 100.0;
                    double score = waitScore * properties.getWaitingWeight()
                            + distanceScore * properties.getDistanceWeight();
                    return new ScoredCandidate(candidate, roundOneDecimal(score));
                })
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparingInt(value -> value.candidate().waitMinutes())
                        .thenComparingDouble(value -> value.candidate().distanceKm())
                        .thenComparing(value -> value.candidate().date())
                        .thenComparing(value -> value.candidate().timeSlot())
                        .thenComparing(value -> value.candidate().branch().getId()))
                .limit(limit)
                .toList();
    }

    private List<RecommendationItemResponse> toResponsesAndSave(
            UUID consultationId,
            List<ScoredCandidate> selected,
            LocalDate today,
            boolean gpsBased
    ) {
        List<Recommendation> entities = new ArrayList<>();
        List<RecommendationItemResponse> responses = new ArrayList<>();

        for (int index = 0; index < selected.size(); index++) {
            int rank = index + 1;
            ScoredCandidate scored = selected.get(index);
            RecommendationCandidate candidate = scored.candidate();
            String dayLabel = dayLabel(candidate.date(), today);
            String timeLabel = timeLabel(candidate.timeSlot());
            String sentence = sentence(candidate, dayLabel, timeLabel);

            entities.add(new Recommendation(
                    consultationId.toString(),
                    candidate.branch(),
                    rank,
                    candidate.date(),
                    candidate.timeSlot(),
                    candidate.waitMinutes(),
                    BigDecimal.valueOf(scored.score()).setScale(1, RoundingMode.HALF_UP),
                    sentence
            ));
            responses.add(new RecommendationItemResponse(
                    rank,
                    new BranchResponse(
                            candidate.branch().getId(),
                            candidate.branch().getName(),
                            candidate.branch().getAddress(),
                            candidate.branch().getPhone(),
                            gpsBased ? roundOneDecimal(candidate.distanceKm()) : null
                    ),
                    new VisitTimeResponse(
                            candidate.date(),
                            dayLabel,
                            candidate.timeSlot(),
                            timeLabel
                    ),
                    candidate.waitMinutes(),
                    candidate.source(),
                    scored.score(),
                    sentence
            ));
        }

        recommendationRepository.saveAll(entities);
        return List.copyOf(responses);
    }

    private List<LocalDate> businessDates(LocalDate startDate, int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = startDate;
        while (dates.size() < count) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }

    private boolean isPastOrStarted(LocalDate date, String timeSlot, LocalDateTime now) {
        if (!date.equals(now.toLocalDate())) {
            return false;
        }
        LocalTime startTime = LocalTime.parse(timeSlot.substring(0, 5));
        return !startTime.isAfter(now.toLocalTime());
    }

    private Range range(Collection<? extends Number> values) {
        double min = values.stream().mapToDouble(Number::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Number::doubleValue).max().orElse(0);
        return new Range(min, max);
    }

    private double inverseScore(double value, Range range) {
        if (Double.compare(range.min(), range.max()) == 0) {
            return 100.0;
        }
        return 100.0 * (range.max() - value) / (range.max() - range.min());
    }

    private String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "오늘";
        }
        if (date.equals(today.plusDays(1))) {
            return "내일";
        }
        return date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 "
                + koreanDayOfWeek(date.getDayOfWeek()) + "요일";
    }

    private String koreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private String timeLabel(String timeSlot) {
        int hour = Integer.parseInt(timeSlot.substring(0, 2));
        String meridiem = hour < 12 ? "오전" : "오후";
        int displayHour = hour % 12 == 0 ? 12 : hour % 12;
        return meridiem + " " + displayHour + "시";
    }

    private String sentence(RecommendationCandidate candidate, String dayLabel, String timeLabel) {
        return "%s %s에 %s 방문을 추천해요. 예상 대기시간은 %d분이에요."
                .formatted(dayLabel, timeLabel, candidate.branch().getName(), candidate.waitMinutes());
    }

    private double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private record BranchDistance(Branch branch, double distanceKm) {
    }

    private record CongestionKey(Long branchId, int dayOfWeek, String timeSlot) {
    }

    private record RecommendationCandidate(
            Branch branch,
            LocalDate date,
            String timeSlot,
            int waitMinutes,
            CongestionSource source,
            double distanceKm
    ) {
    }

    private record ScoredCandidate(RecommendationCandidate candidate, double score) {
    }

    private record Range(double min, double max) {
    }
}
