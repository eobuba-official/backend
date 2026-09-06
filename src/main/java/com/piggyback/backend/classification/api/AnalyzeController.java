package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.AnalyzeResult;
import com.piggyback.backend.classification.application.TaskClassificationWorkflow;
import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "업무 분류", description = "자연어를 은행 업무로 분류하고 사기 위험을 우선 확인합니다.")
@SecurityRequirement(name = "bearerAuth")
public class AnalyzeController {

    private final TaskClassificationWorkflow workflow;

    @PostMapping("/analyze")
    @Operation(
            summary = "자연어 업무 분석",
            description = """
                    시니어의 자연어를 8종 은행 업무 코드로 분류합니다.
                    확신도에 따라 CONFIRMED, CANDIDATES, UNCLASSIFIED로 정규화하며,
                    유효한 사기 패턴이 있으면 업무와 방문 판단을 숨기고 FRAUD_WARNING을 먼저 반환합니다.
                    음성 입력은 inputMethod를 VOICE로 보내며 correctedUtterance를 사용자에게 다시 확인해야 합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 완료 또는 사기 경고"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "발화·입력 방식 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "LLM 호출 또는 응답 파싱 실패")
    })
    public ApiResponse<AnalyzeResult> analyze(
            @Parameter(hidden = true)
            @RequestAttribute(value = JwtAuthFilter.USER_ID_ATTRIBUTE, required = false) Long userId,
            @Valid @RequestBody AnalyzeRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(workflow.analyze(userId, request.toCommand()));
    }
}
