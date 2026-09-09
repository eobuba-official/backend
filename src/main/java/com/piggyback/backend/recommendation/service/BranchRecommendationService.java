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
import com.piggyback.backend.recommendation.domain.LocatedBranch;
import com.piggyback.backend.recommendation.domain.LocationQuery;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.dto.BranchResponse;
import com.piggyback.backend.recommendation.dto.RecommendationItemResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지점 × 방문 시간대 후보를 총 소요 시간(도보 + 예상 대기) 기준 오름차순으로 추천한다.
 * totalMinutes = (거리km ÷ 보행속도 km/h × 60) + 예상 대기분
 */
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
    private final BranchLocator branchLocator;
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
        LocationQuery location = LocationQuery.of(lat, lng, regionCode);
        validateLimit(limit);
        validateReferences(userId, consultationId, taskTypeCode);

        int resultLimit = limit == null ? properties.getResultLimit() : limit;
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDate> visitDates = businessDates(now.toLocalDate(), properties.getPlanningBusinessDays());

        List<LocatedBranch> branches = branchLocator.locate(
                branchTaskRepository.findBranchesByTaskTypeCode(taskTypeCode),
                location
        );
        List<RecommendationCandidate> candidates = createCandidates(branches, visitDates, now);
        List<RecommendationCandidate> selected = selectFastest(candidates, resultLimit);

        recommendationRepository.deleteByConsultationId(consultationId.toString());
        List<RecommendationItemResponse> responses = toResponsesAndSave(consultationId, selected, now.toLocalDate());

        return new BranchRecommendationResponse(responses, properties.getWalkingSpeedKmh());
    }

    private void validateLimit(Integer limit) {
        if (limit != null && (limit < 1 || limit > MAX_RESULT_LIMIT)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limit은 1 이상 5 이하여야 합니다.");
        }
    }

    private void validateReferences(Long userId, UUID consultationId, TaskTypeCode taskTypeCode) {
        if (!taskTypeRepository.existsById(taskTypeCode)) {
            throw new TaskTypeNotFoundException();
        }
        if (!consultationReferenceRepository.lockByIdAndUserId(consultationId, userId)) {
            throw new ConsultationNotFoundException();
        }
    }

    private List<RecommendationCandidate> createCandidates(
            List<LocatedBranch> branches,
            List<LocalDate> visitDates,
            LocalDateTime now
    ) {
        if (branches.isEmpty()) {
            return List.of();
        }

        Set<Long> branchIds = branches.stream()
                .map(located -> located.branch().getId())
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
        for (LocatedBranch located : branches) {
            // regionCode 조회는 거리를 모르므로 도보 시간 0분으로 두고 대기 시간만 비교한다.
            double walkMinutes = located.hasDistance() ? branchLocator.walkMinutes(located.distanceKm()) : 0.0;
            for (LocalDate date : visitDates) {
                for (String timeSlot : BUSINESS_TIME_SLOTS) {
                    if (isPastOrStarted(date, timeSlot, now)) {
                        continue;
                    }
                    CongestionSlot congestion = congestionByKey.get(
                            new CongestionKey(located.branch().getId(), date.getDayOfWeek().getValue(), timeSlot)
                    );
                    int waitMinutes = congestion == null
                            ? properties.getDefaultWaitMinutes()
                            : congestion.getExpectedWaitMinutes();
                    CongestionSource source = congestion == null
                            ? CongestionSource.FALLBACK
                            : CongestionSource.MOCK;
                    candidates.add(new RecommendationCandidate(
                            located,
                            date,
                            timeSlot,
                            waitMinutes,
                            source,
                            walkMinutes,
                            walkMinutes + waitMinutes
                    ));
                }
            }
        }
        return candidates;
    }

    private List<RecommendationCandidate> selectFastest(List<RecommendationCandidate> candidates, int limit) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble(RecommendationCandidate::totalMinutes)
                        .thenComparingInt(RecommendationCandidate::waitMinutes)
                        .thenComparingDouble(RecommendationCandidate::walkMinutes)
                        .thenComparing(RecommendationCandidate::date)
                        .thenComparing(RecommendationCandidate::timeSlot)
                        .thenComparing(candidate -> candidate.branch().getId()))
                .limit(limit)
                .toList();
    }

    private List<RecommendationItemResponse> toResponsesAndSave(
            UUID consultationId,
            List<RecommendationCandidate> selected,
            LocalDate today
    ) {
        List<Recommendation> entities = new ArrayList<>();
        List<RecommendationItemResponse> responses = new ArrayList<>();

        for (int index = 0; index < selected.size(); index++) {
            int rank = index + 1;
            RecommendationCandidate candidate = selected.get(index);
            Branch branch = candidate.branch();
            String dayLabel = dayLabel(candidate.date(), today);
            String timeLabel = timeLabel(candidate.timeSlot());
            Integer walkMinutes = candidate.located().hasDistance()
                    ? (int) Math.round(candidate.walkMinutes())
                    : null;
            String sentence = sentence(candidate, dayLabel, timeLabel, walkMinutes);

            entities.add(new Recommendation(
                    consultationId.toString(),
                    branch,
                    rank,
                    candidate.date(),
                    candidate.timeSlot(),
                    candidate.waitMinutes(),
                    BigDecimal.valueOf(candidate.totalMinutes()).setScale(1, RoundingMode.HALF_UP),
                    sentence
            ));
            responses.add(new RecommendationItemResponse(
                    rank,
                    new BranchResponse(
                            branch.getId(),
                            branch.getName(),
                            branch.getAddress(),
                            branch.getPhone(),
                            branch.getLat().doubleValue(),
                            branch.getLng().doubleValue(),
                            candidate.located().hasDistance()
                                    ? roundOneDecimal(candidate.located().distanceKm())
                                    : null
                    ),
                    new VisitTimeResponse(
                            candidate.date(),
                            dayLabel,
                            candidate.timeSlot(),
                            timeLabel
                    ),
                    candidate.waitMinutes(),
                    candidate.source(),
                    walkMinutes,
                    (int) Math.round(candidate.totalMinutes()),
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

    private String sentence(
            RecommendationCandidate candidate,
            String dayLabel,
            String timeLabel,
            Integer walkMinutes
    ) {
        String head = "%s %s에 %s 방문을 추천해요."
                .formatted(dayLabel, timeLabel, candidate.branch().getName());
        if (walkMinutes == null) {
            return head + " 예상 대기시간은 %d분이에요.".formatted(candidate.waitMinutes());
        }
        return head + " 걸어서 %d분, 예상 대기시간은 %d분이에요.".formatted(walkMinutes, candidate.waitMinutes());
    }

    private double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record CongestionKey(Long branchId, int dayOfWeek, String timeSlot) {
    }

    private record RecommendationCandidate(
            LocatedBranch located,
            LocalDate date,
            String timeSlot,
            int waitMinutes,
            CongestionSource source,
            double walkMinutes,
            double totalMinutes
    ) {
        Branch branch() {
            return located.branch();
        }
    }
}
