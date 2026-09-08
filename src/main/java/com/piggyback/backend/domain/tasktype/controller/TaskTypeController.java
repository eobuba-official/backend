package com.piggyback.backend.domain.tasktype.controller;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.tasktype.dto.TaskTypeListResponse;
import com.piggyback.backend.domain.tasktype.service.TaskTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-types")
@Tag(name = "업무 유형", description = "어부바가 지원하는 은행 업무 유형을 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class TaskTypeController {

    private final TaskTypeService taskTypeService;

    @GetMapping
    @Operation(summary = "은행 업무 유형 전체 조회", description = "업무 코드와 사용자에게 표시할 업무 이름 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업무 유형 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    public ApiResponse<TaskTypeListResponse> getTaskTypes() {
        return ApiResponse.success(taskTypeService.getTaskTypes());
    }
}
