package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.AnalyzeResult;
import com.piggyback.backend.classification.application.CorrectionConfirmationWorkflow;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consultations")
@Tag(name = "업무 분류", description = "자연어를 은행 업무로 분류하고 사기 위험을 우선 확인합니다.")
@SecurityRequirement(name = "bearerAuth")
public class CorrectionConfirmationController {

    private final CorrectionConfirmationWorkflow workflow;

    public CorrectionConfirmationController(CorrectionConfirmationWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/{consultationId}/correction-confirmation")
    @Operation(
            summary = "Gemini 보정 문장 확인",
            description = "사용자가 승인하거나 직접 수정한 문장으로 동일 상담을 다시 분석합니다. taskTypeCode를 전달하면 사기 검사 후 사용자 선택 업무를 확정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "보정 확인 및 상담 갱신 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "확정 문장 또는 업무 코드 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "보정 확인이 가능한 상담 상태가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "LLM 호출 또는 응답 파싱 실패")
    })
    public ApiResponse<AnalyzeResult> confirm(
            @Parameter(hidden = true)
            @RequestAttribute(value = JwtAuthFilter.USER_ID_ATTRIBUTE, required = false) Long userId,
            @Parameter(description = "보정 확인이 필요한 상담 UUID", required = true)
            @PathVariable UUID consultationId,
            @Valid @RequestBody CorrectionConfirmationRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(workflow.confirm(
                userId,
                consultationId,
                request.confirmedUtterance(),
                request.taskTypeCode()
        ));
    }
}
