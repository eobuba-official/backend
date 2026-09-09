package com.piggyback.backend.domain.branch.service;

import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.branch.dto.NearbyBranchResponse;
import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.recommendation.config.RecommendationProperties;
import com.piggyback.backend.recommendation.service.BranchLocator;
import com.piggyback.backend.recommendation.service.DistanceCalculator;
import com.piggyback.backend.repository.BranchRepository;
import com.piggyback.backend.repository.BranchTaskRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NearbyBranchServiceTest {

    private static final double USER_LAT = 37.5665;
    private static final double USER_LNG = 126.9780;

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private BranchTaskRepository branchTaskRepository;
    @Mock
    private TaskTypeRepository taskTypeRepository;

    private NearbyBranchService service;

    @BeforeEach
    void setUp() {
        RecommendationProperties properties = new RecommendationProperties();
        service = new NearbyBranchService(
                branchRepository,
                branchTaskRepository,
                taskTypeRepository,
                new BranchLocator(new DistanceCalculator(), properties),
                properties
        );
    }

    @Test
    void returnsBranchesSortedByDistanceWithWalkMinutes() {
        Branch jongno = branch(103L, "종로지점", 37.5700, 126.9820, "1111013500");
        Branch euljiro = branch(120L, "을지로지점", 37.5660, 126.9910, "1114012000");
        Branch gangnam = branch(200L, "강남지점", 37.4979, 127.0276, "1168010100");
        when(branchRepository.findAll()).thenReturn(List.of(gangnam, euljiro, jongno));

        NearbyBranchResponse response = service.findNearby(null, USER_LAT, USER_LNG, null, null);

        assertThat(response.branches())
                .extracting(NearbyBranchResponse.NearbyBranchItem::branchId)
                .containsExactly(103L, 120L, 200L);
        NearbyBranchResponse.NearbyBranchItem first = response.branches().get(0);
        assertThat(first.name()).isEqualTo("종로지점");
        assertThat(first.lat()).isEqualTo(37.57);
        assertThat(first.lng()).isEqualTo(126.982);
        assertThat(first.distanceKm()).isEqualTo(0.5);
        assertThat(first.walkMinutes()).isEqualTo(8);
        assertThat(response.walkingSpeedKmh()).isEqualTo(4.0);
        verify(taskTypeRepository, never()).existsById(PASSBOOK_REISSUE);
    }

    @Test
    void excludesBranchesOutsideSearchRadius() {
        Branch jongno = branch(103L, "종로지점", 37.5700, 126.9820, "1111013500");
        Branch busan = branch(300L, "부산지점", 35.1796, 129.0756, "2611010100");
        when(branchRepository.findAll()).thenReturn(List.of(busan, jongno));

        NearbyBranchResponse response = service.findNearby(null, USER_LAT, USER_LNG, null, null);

        assertThat(response.branches())
                .extracting(NearbyBranchResponse.NearbyBranchItem::branchId)
                .containsExactly(103L);
    }

    @Test
    void appliesDefaultLimitOfFive() {
        List<Branch> sevenBranches = List.of(
                branch(1L, "지점1", 37.5670, 126.9781, "1111010100"),
                branch(2L, "지점2", 37.5675, 126.9782, "1111010100"),
                branch(3L, "지점3", 37.5680, 126.9783, "1111010100"),
                branch(4L, "지점4", 37.5685, 126.9784, "1111010100"),
                branch(5L, "지점5", 37.5690, 126.9785, "1111010100"),
                branch(6L, "지점6", 37.5695, 126.9786, "1111010100"),
                branch(7L, "지점7", 37.5700, 126.9787, "1111010100")
        );
        when(branchRepository.findAll()).thenReturn(sevenBranches);

        NearbyBranchResponse response = service.findNearby(null, USER_LAT, USER_LNG, null, null);

        assertThat(response.branches()).hasSize(5);
        assertThat(response.branches().get(0).branchId()).isEqualTo(1L);
    }

    @Test
    void filtersByTaskTypeWhenProvided() {
        Branch jongno = branch(103L, "종로지점", 37.5700, 126.9820, "1111013500");
        when(taskTypeRepository.existsById(PASSBOOK_REISSUE)).thenReturn(true);
        when(branchTaskRepository.findBranchesByTaskTypeCode(PASSBOOK_REISSUE)).thenReturn(List.of(jongno));

        NearbyBranchResponse response = service.findNearby(PASSBOOK_REISSUE, USER_LAT, USER_LNG, null, 3);

        assertThat(response.branches())
                .extracting(NearbyBranchResponse.NearbyBranchItem::branchId)
                .containsExactly(103L);
        verify(branchRepository, never()).findAll();
    }

    @Test
    void filtersByRegionCodeWithoutDistance() {
        Branch jongno = branch(103L, "종로지점", 37.5700, 126.9820, "1111013500");
        Branch euljiro = branch(120L, "을지로지점", 37.5660, 126.9910, "1114012000");
        when(branchRepository.findAll()).thenReturn(List.of(jongno, euljiro));

        NearbyBranchResponse response = service.findNearby(null, null, null, "1114012000", null);

        assertThat(response.branches()).singleElement().satisfies(item -> {
            assertThat(item.branchId()).isEqualTo(120L);
            assertThat(item.distanceKm()).isNull();
            assertThat(item.walkMinutes()).isNull();
        });
    }

    @Test
    void rejectsRequestWithoutGpsOrRegionCode() {
        assertThatThrownBy(() -> service.findNearby(null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(branchRepository, never()).findAll();
    }

    @Test
    void rejectsLimitAboveTwenty() {
        assertThatThrownBy(() -> service.findNearby(null, USER_LAT, USER_LNG, null, 21))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void throwsWhenTaskTypeDoesNotExist() {
        when(taskTypeRepository.existsById(PASSBOOK_REISSUE)).thenReturn(false);

        assertThatThrownBy(() -> service.findNearby(PASSBOOK_REISSUE, USER_LAT, USER_LNG, null, null))
                .isInstanceOf(TaskTypeNotFoundException.class);
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
