package com.piggyback.backend.domain.tasktype.controller;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.tasktype.dto.TaskTypeListResponse;
import com.piggyback.backend.domain.tasktype.service.TaskTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-types")
public class TaskTypeController {

    private final TaskTypeService taskTypeService;

    @GetMapping
    public ApiResponse<TaskTypeListResponse> getTaskTypes() {
        return ApiResponse.success(taskTypeService.getTaskTypes());
    }
}
