package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.AnalyzeResult;
import com.piggyback.backend.classification.application.TaskClassificationWorkflow;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.response.ApiResponse;
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
public class AnalyzeController {

    private final TaskClassificationWorkflow workflow;

    @PostMapping("/analyze")
    public ApiResponse<AnalyzeResult> analyze(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody AnalyzeRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(workflow.analyze(userId, request.toCommand()));
    }
}
