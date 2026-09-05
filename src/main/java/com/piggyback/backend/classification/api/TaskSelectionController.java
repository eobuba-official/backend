package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.SelectedTaskOutcome;
import com.piggyback.backend.classification.application.TaskSelectionWorkflow;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.response.ApiResponse;
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
public class TaskSelectionController {

    private final TaskSelectionWorkflow workflow;

    public TaskSelectionController(TaskSelectionWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/{consultationId}/task-selection")
    public ApiResponse<SelectedTaskOutcome> select(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable UUID consultationId,
            @Valid @RequestBody TaskSelectionRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(workflow.select(userId, consultationId, request.taskTypeCode()));
    }
}
