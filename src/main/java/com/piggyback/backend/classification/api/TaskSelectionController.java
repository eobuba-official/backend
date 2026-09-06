package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.SelectedTaskOutcome;
import com.piggyback.backend.classification.application.TaskSelectionWorkflow;
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
public class TaskSelectionController {

    private final TaskSelectionWorkflow workflow;

    public TaskSelectionController(TaskSelectionWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/{consultationId}/task-selection")
    @Operation(
            summary = "업무 후보 선택",
            description = "CANDIDATES_SUGGESTED 상담에서 저장된 후보 하나를 선택합니다. LLM을 다시 호출하지 않고 업무를 확정한 뒤 방문 판단을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업무 확정 및 방문 판단 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "업무 코드 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담이 없거나 저장된 후보가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "후보 선택이 가능한 상담 상태가 아님")
    })
    public ApiResponse<SelectedTaskOutcome> select(
            @Parameter(hidden = true)
            @RequestAttribute(value = JwtAuthFilter.USER_ID_ATTRIBUTE, required = false) Long userId,
            @Parameter(description = "분석 응답에서 받은 상담 UUID", required = true)
            @PathVariable UUID consultationId,
            @Valid @RequestBody TaskSelectionRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(workflow.select(userId, consultationId, request.taskTypeCode()));
    }
}
