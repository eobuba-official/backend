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
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.domain.CongestionSource;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
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
                new DistanceCalculator(),
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
        assertThat(response.recommendations().get(0).visitTime().date().toString()).isEqualTo("2026-09-04");
        assertThat(response.recommendations().get(0).visitTime().dayLabel()).isEqualTo("내일");
        assertThat(response.recommendations().get(0).visitTime().timeLabel()).isEqualTo("오전 10시");
        assertThat(response.recommendations().get(0).expectedWaitMinutes()).isEqualTo(5);
        assertThat(response.recommendations().get(0).congestionSource()).isEqualTo(CongestionSource.MOCK);
        assertThat(response.recommendations().get(0).rank()).isEqualTo(1);
        assertThat(response.weights().waiting()).isEqualTo(0.6);
        assertThat(response.weights().distance()).isEqualTo(0.4);
        InOrder updateOrder = Mockito.inOrder(consultationReferenceRepository, recommendationRepository);
        updateOrder.verify(consultationReferenceRepository).lockByIdAndUserId(CONSULTATION_ID, USER_ID);
        updateOrder.verify(recommendationRepository).deleteByConsultationId(CONSULTATION_ID.toString());
        updateOrder.verify(recommendationRepository).saveAll(anyList());
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
        });
    }

    @Test
    void waitingTimeHasMoreWeightThanDistance() {
        Branch near = branch(87L, "가까운 지점", 37.5666, 126.9781, "1111011200");
        Branch far = branch(103L, "대기가 짧은 지점", 37.6100, 127.0200, "1111013500");
        givenValidReferences();
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(near, far));
        when(congestionSlotRepository.findForRecommendation(Set.of(87L, 103L), Set.of(4, 5)))
                .thenReturn(List.of(new CongestionSlot(far, 5, "10:00-11:00", 5)));

        BranchRecommendationResponse response = recommendWithGps(1);

        assertThat(response.recommendations().get(0).branch().branchId()).isEqualTo(103L);
        assertThat(response.recommendations().get(0).expectedWaitMinutes()).isEqualTo(5);
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
