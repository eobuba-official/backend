package com.piggyback.backend.checklist.controller;

import com.piggyback.backend.checklist.dto.ChecklistResponse;
import com.piggyback.backend.checklist.service.ChecklistService;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-types")
@Tag(name = "준비물", description = "업무별 준비물과 사용자 답변을 반영한 최종 준비물을 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class ChecklistController {

    private final ChecklistService checklistService;

    @GetMapping("/{taskTypeCode}/checklist")
    @Operation(
            summary = "업무별 준비물 조회",
            description = "선택한 은행 업무에 필요한 필수 준비물과 조건부 준비물 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "준비물 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "지원하지 않는 업무 유형")
    })
    public ApiResponse<ChecklistResponse> getChecklist(
            @Parameter(description = "은행 업무 유형 코드", required = true, example = "PASSBOOK_REISSUE")
            @PathVariable TaskTypeCode taskTypeCode
    ) {
        return ApiResponse.success(checklistService.getChecklist(taskTypeCode));
    }
}
