package com.piggyback.backend.recommendation.controller;

import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.recommendation.dto.BranchRecommendationResponse;
import com.piggyback.backend.recommendation.service.BranchRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/branches")
@Tag(name = "지점 추천", description = "업무 처리 가능 여부, 위치와 예상 대기시간을 반영해 방문 지점을 추천합니다.")
@SecurityRequirement(name = "bearerAuth")
public class BranchRecommendationController {

    private final BranchRecommendationService branchRecommendationService;

    @GetMapping("/recommendations")
    @Operation(
            summary = "방문 지점 및 시간 추천",
            description = "상담과 업무 유형을 기준으로 방문 가능한 지점을 필터링하고 거리와 예상 대기시간을 계산해 추천합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지점 추천 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "위치 또는 조회 조건 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담 또는 추천 가능한 지점이 없음")
    })
    public ApiResponse<BranchRecommendationResponse> getRecommendations(
            @Parameter(hidden = true)
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @Parameter(description = "분석 응답에서 받은 상담 UUID", required = true)
            @RequestParam UUID consultationId,
            @Parameter(description = "추천 지점이 처리해야 하는 은행 업무 유형 코드", required = true, example = "PASSBOOK_REISSUE")
            @RequestParam TaskTypeCode taskTypeCode,
            @Parameter(description = "사용자 현재 위치의 위도. 경도와 함께 입력합니다.", example = "37.5665")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "사용자 현재 위치의 경도. 위도와 함께 입력합니다.", example = "126.9780")
            @RequestParam(required = false) Double lng,
            @Parameter(description = "위치 좌표 대신 사용할 행정구역 코드")
            @RequestParam(required = false) String regionCode,
            @Parameter(description = "반환할 최대 지점 수")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(branchRecommendationService.recommend(
                userId,
                consultationId,
                taskTypeCode,
                lat,
                lng,
                regionCode,
                limit
        ));
    }
}
