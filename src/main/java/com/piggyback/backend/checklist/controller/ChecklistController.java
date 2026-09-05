package com.piggyback.backend.checklist.controller;

import com.piggyback.backend.checklist.dto.ChecklistResponse;
import com.piggyback.backend.checklist.service.ChecklistService;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task-types")
public class ChecklistController {

    private final ChecklistService checklistService;

    @GetMapping("/{taskTypeCode}/checklist")
    public ApiResponse<ChecklistResponse> getChecklist(
            @PathVariable TaskTypeCode taskTypeCode
    ) {
        return ApiResponse.success(checklistService.getChecklist(taskTypeCode));
    }
}
