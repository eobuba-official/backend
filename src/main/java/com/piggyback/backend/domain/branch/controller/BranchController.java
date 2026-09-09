package com.piggyback.backend.domain.branch.controller;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.branch.dto.NearbyBranchResponse;
import com.piggyback.backend.domain.branch.service.NearbyBranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/branches")
@Tag(name = "지점 조회", description = "상담과 무관하게 현재 위치 기준으로 가까운 지점 정보를 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final NearbyBranchService nearbyBranchService;

    @GetMapping("/nearby")
    @Operation(
            summary = "가까운 지점 조회",
            description = "consultationId 없이 호출합니다. lat/lng 조회 시 반경 내 지점을 거리 오름차순으로, "
                    + "regionCode 조회 시 해당 법정동의 지점을 이름순으로 반환합니다. "
                    + "walkMinutes는 거리 ÷ 보행속도(walkingSpeedKmh, 기본 4km/h)로 계산한 도보 시간(분)입니다. "
                    + "추천 이력은 저장하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지점 조회 완료. 조건에 맞는 지점이 없으면 빈 목록 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "업무 유형 코드, 위치 또는 limit 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "업무 유형을 찾을 수 없음")
    })
    public ApiResponse<NearbyBranchResponse> getNearbyBranches(
            @Parameter(description = "사용자 현재 위치의 위도. 경도와 함께 입력합니다.", example = "37.5665")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "사용자 현재 위치의 경도. 위도와 함께 입력합니다.", example = "126.9780")
            @RequestParam(required = false) Double lng,
            @Parameter(description = "위치 좌표 대신 사용할 법정동코드 10자리")
            @RequestParam(required = false) String regionCode,
            @Parameter(description = "해당 업무를 처리할 수 있는 지점만 조회할 때 지정하는 업무 유형 코드", example = "PASSBOOK_REISSUE")
            @RequestParam(required = false) TaskTypeCode taskTypeCode,
            @Parameter(description = "반환할 최대 지점 수 (1~20, 기본 5)")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(nearbyBranchService.findNearby(taskTypeCode, lat, lng, regionCode, limit));
    }
}
