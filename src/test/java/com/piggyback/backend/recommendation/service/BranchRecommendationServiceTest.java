package com.piggyback.backend.recommendation.service;

import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.entity.CongestionSlot;
import com.piggyback.backend.entity.Recommendation;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.domain.CongestionSource;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.dto.RecommendationItemResponse;
import com.piggyback.backend.recommendation.exception.ConsultationNotFoundException;
import com.piggyback.backend.recommendation.repository.ConsultationReferenceRepository;
import com.piggyback.backend.repository.BranchTaskRepository;
import com.piggyback.backend.repository.CongestionSlotRepository;
import com.piggyback.backend.repository.RecommendationRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchRecommendationServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID CONSULTATION_ID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final double USER_LAT = 37.5665;
    private static final double USER_LNG = 126.9780;

    @Mock
    private TaskTypeRepository taskTypeRepository;
    @Mock
    private BranchTaskRepository branchTaskRepository;
    @Mock
    private CongestionSlotRepository congestionSlotRepository;
    @Mock
    private RecommendationRepository recommendationRepository;
    @Mock
    private ConsultationReferenceRepository consultationReferenceRepository;

    private BranchRecommendationService service;
    private RecommendationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RecommendationProperties();
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-03T03:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new BranchRecommendationService(
                taskTypeRepository,
                branchTaskRepository,
                congestionSlotRepository,
                recommendationRepository,
                consultationReferenceRepository,
                properties,
                new BranchLocator(new DistanceCalculator(), properties),
                clock
        );
    }

    @Test
    void recommendsMockSlotAndPersistsRankedResults() {
        Branch jongno = branch(103L, "KB국민은행 종로지점", 37.5700, 126.9820, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(jongno));
        when(congestionSlotRepository.findForRecommendation(Set.of(103L), Set.of(4, 5)))
                .thenReturn(List.of(new CongestionSlot(jongno, 5, "10:00-11:00", 5)));

        BranchRecommendationResponse response = recommendWithGps(null);

        assertThat(response.recommendations()).hasSize(3);
        RecommendationItemResponse first = response.recommendations().get(0);
        assertThat(first.visitTime().date().toString()).isEqualTo("2026-09-04");
        assertThat(first.visitTime().dayLabel()).isEqualTo("내일");
        assertThat(first.visitTime().timeLabel()).isEqualTo("오전 10시");
        assertThat(first.expectedWaitMinutes()).isEqualTo(5);
        assertThat(first.congestionSource()).isEqualTo(CongestionSource.MOCK);
        assertThat(first.rank()).isEqualTo(1);
        // 약 0.53km → 도보 8분, 대기 5분 → 총 13분
        assertThat(first.branch().distanceKm()).isEqualTo(0.5);
        assertThat(first.walkMinutes()).isEqualTo(8);
        assertThat(first.totalMinutes()).isEqualTo(13);
        assertThat(first.sentence()).isEqualTo("내일 오전 10시에 KB국민은행 종로지점 방문을 추천해요. 걸어서 8분, 예상 대기시간은 5분이에요.");
        assertThat(response.walkingSpeedKmh()).isEqualTo(4.0);

        InOrder updateOrder = Mockito.inOrder(consultationReferenceRepository, recommendationRepository);
        updateOrder.verify(consultationReferenceRepository).lockByIdAndUserId(CONSULTATION_ID, USER_ID);
        updateOrder.verify(recommendationRepository).deleteByConsultationId(CONSULTATION_ID.toString());
        ArgumentCaptor<List<Recommendation>> saved = ArgumentCaptor.captor();
        updateOrder.verify(recommendationRepository).saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getTotalMinutes()).isEqualByComparingTo(new BigDecimal("12.9"));
    }

    @Test
    void appliesFifteenMinuteFallbackWhenSlotDataIsMissing() {
        Branch jongno = branch(103L, "KB국민은행 종로지점", 37.5700, 126.9820, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(jongno));
        when(congestionSlotRepository.findForRecommendation(Set.of(103L), Set.of(4, 5))).thenReturn(List.of());

        BranchRecommendationResponse response = recommendWithGps(1);

        assertThat(response.recommendations()).singleElement().satisfies(result -> {
            assertThat(result.expectedWaitMinutes()).isEqualTo(15);
            assertThat(result.congestionSource()).isEqualTo(CongestionSource.FALLBACK);
            assertThat(result.visitTime().timeSlot()).isEqualTo("13:00-14:00");
            assertThat(result.totalMinutes()).isEqualTo(23);
        });
    }

    @Test
    void nearBranchWinsWhenWalkingTimeOutweighsShorterWait() {
        // 가까운 지점: 약 0.01km(도보 0분) + 대기 15분 = 15분
        // 먼 지점: 약 6km(도보 약 90분) + 대기 5분 ≈ 95분
        Branch near = branch(87L, "가까운 지점", 37.5666, 126.9781, "1111011200");
        Branch far = branch(103L, "대기가 짧은 지점", 37.6100, 127.0200, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(near, far));
        when(congestionSlotRepository.findForRecommendation(Set.of(87L, 103L), Set.of(4, 5)))
                .thenReturn(List.of(new CongestionSlot(far, 5, "10:00-11:00", 5)));

        BranchRecommendationResponse response = recommendWithGps(1);

        assertThat(response.recommendations().get(0).branch().branchId()).isEqualTo(87L);
        assertThat(response.recommendations().get(0).expectedWaitMinutes()).isEqualTo(15);
        assertThat(response.recommendations().get(0).totalMinutes()).isEqualTo(15);
    }

    @Test
    void fartherBranchWinsWhenWaitSavingExceedsExtraWalk() {
        // 가까운 지점: 도보 0분 + 대기 40분 = 40분
        // 먼 지점: 약 1km(도보 약 16분) + 대기 5분 ≈ 21분
        Branch near = branch(87L, "붐비는 지점", 37.5666, 126.9781, "1111011200");
        Branch far = branch(103L, "한산한 지점", 37.5755, 126.9780, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(near, far));
        when(congestionSlotRepository.findForRecommendation(Set.of(87L, 103L), Set.of(4, 5)))
                .thenReturn(List.of(
                        new CongestionSlot(near, 4, "13:00-14:00", 40),
                        new CongestionSlot(near, 4, "14:00-15:00", 40),
                        new CongestionSlot(near, 4, "15:00-16:00", 40),
                        new CongestionSlot(near, 5, "09:00-10:00", 40),
                        new CongestionSlot(near, 5, "10:00-11:00", 40),
                        new CongestionSlot(near, 5, "11:00-12:00", 40),
                        new CongestionSlot(near, 5, "12:00-13:00", 40),
                        new CongestionSlot(near, 5, "13:00-14:00", 40),
                        new CongestionSlot(near, 5, "14:00-15:00", 40),
                        new CongestionSlot(near, 5, "15:00-16:00", 40),
                        new CongestionSlot(far, 4, "13:00-14:00", 5)
                ));

        BranchRecommendationResponse response = recommendWithGps(2);

        assertThat(response.recommendations())
                .extracting(item -> item.branch().branchId())
                .containsExactly(103L, 103L);
        RecommendationItemResponse first = response.recommendations().get(0);
        assertThat(first.visitTime().timeSlot()).isEqualTo("13:00-14:00");
        assertThat(first.walkMinutes()).isEqualTo(15);
        assertThat(first.totalMinutes()).isEqualTo(20);
    }

    @Test
    void ranksByTotalMinutesAscending() {
        Branch jongno = branch(103L, "KB국민은행 종로지점", 37.5700, 126.9820, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(jongno));
        when(congestionSlotRepository.findForRecommendation(Set.of(103L), Set.of(4, 5)))
                .thenReturn(List.of(
                        new CongestionSlot(jongno, 4, "13:00-14:00", 30),
                        new CongestionSlot(jongno, 5, "10:00-11:00", 3)
                ));

        BranchRecommendationResponse response = recommendWithGps(5);

        assertThat(response.recommendations())
                .extracting(RecommendationItemResponse::totalMinutes)
                .isSorted();
        assertThat(response.recommendations().get(0).visitTime().timeSlot()).isEqualTo("10:00-11:00");
        assertThat(response.recommendations().get(0).expectedWaitMinutes()).isEqualTo(3);
        // 대기 30분 슬롯은 기본 대기 15분 슬롯들보다 뒤로 밀려 상위 5개에 들지 못한다
        assertThat(response.recommendations())
                .noneMatch(item -> item.expectedWaitMinutes() == 30);
    }

    @Test
    void filtersByRegionCodeWhenGpsIsNotProvided() {
        Branch jongno = branch(103L, "종로지점", 37.5700, 126.9820, "1111013500");
        Branch euljiro = branch(120L, "을지로지점", 37.5660, 126.9910, "1114012000");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE))
                .thenReturn(List.of(jongno, euljiro));
        when(congestionSlotRepository.findForRecommendation(Set.of(103L), Set.of(4, 5))).thenReturn(List.of());

        BranchRecommendationResponse response = service.recommend(
                USER_ID,
                CONSULTATION_ID,
                PASSBOOK_REISSUE,
                null,
                null,
                "1111013500",
                1
        );

        assertThat(response.recommendations()).singleElement().satisfies(result -> {
            assertThat(result.branch().branchId()).isEqualTo(103L);
            assertThat(result.branch().distanceKm()).isNull();
            assertThat(result.walkMinutes()).isNull();
            assertThat(result.totalMinutes()).isEqualTo(15);
            assertThat(result.sentence()).isEqualTo("오늘 오후 1시에 종로지점 방문을 추천해요. 예상 대기시간은 15분이에요.");
        });
    }

    @Test
    void rejectsRequestWithoutGpsOrRegionCode() {
        assertThatThrownBy(() -> service.recommend(
                USER_ID,
                CONSULTATION_ID,
                PASSBOOK_REISSUE,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(taskTypeRepository, never()).existsById(PASSBOOK_REISSUE);
    }

    @Test
    void rejectsLimitAboveFive() {
        assertThatThrownBy(() -> recommendWithGps(6))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void throwsWhenTaskTypeDoesNotExist() {
        when(taskTypeRepository.existsById(PASSBOOK_REISSUE)).thenReturn(false);

        assertThatThrownBy(() -> recommendWithGps(null))
                .isInstanceOf(TaskTypeNotFoundException.class);
    }

    @Test
    void throwsWhenConsultationDoesNotBelongToUser() {
        when(taskTypeRepository.existsById(PASSBOOK_REISSUE)).thenReturn(true);
        when(consultationReferenceRepository.lockByIdAndUserId(CONSULTATION_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> recommendWithGps(null))
                .isInstanceOf(ConsultationNotFoundException.class);
    }

    private BranchRecommendationResponse recommendWithGps(Integer limit) {
        return service.recommend(
                USER_ID,
                CONSULTATION_ID,
                PASSBOOK_REISSUE,
                USER_LAT,
                USER_LNG,
                null,
                limit
        );
    }

    private void givenValidReferences() {
        when(taskTypeRepository.existsById(PASSBOOK_REISSUE)).thenReturn(true);
        when(consultationReferenceRepository.lockByIdAndUserId(CONSULTATION_ID, USER_ID)).thenReturn(true);
    }

    private Branch branch(Long id, String name, double lat, double lng, String regionCode) {
        return new Branch(
                id,
                name,
                "서울시 테스트 주소",
                "02-000-0000",
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng),
                regionCode
        );
    }
}
