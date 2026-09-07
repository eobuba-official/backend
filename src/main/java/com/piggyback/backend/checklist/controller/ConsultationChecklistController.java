package com.piggyback.backend.checklist.controller;

import com.piggyback.backend.checklist.dto.ChecklistAnswerRequest;
import com.piggyback.backend.checklist.dto.ChecklistAnswerResponse;
import com.piggyback.backend.checklist.dto.ChecklistQuestionsResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistResponse;
import com.piggyback.backend.checklist.service.ConsultationChecklistService;
import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consultations/{consultationId}/checklist")
public class ConsultationChecklistController {

    private final ConsultationChecklistService consultationChecklistService;

    @GetMapping("/questions")
    @Operation(summary = "조건부 준비물 질문 조회")
    public ApiResponse<ChecklistQuestionsResponse> getQuestions(
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable UUID consultationId
    ) {
        return ApiResponse.success(consultationChecklistService.getQuestions(userId, consultationId));
    }

    @PutMapping("/answers")
    @Operation(summary = "조건부 준비물 답변 저장")
    public ApiResponse<ChecklistAnswerResponse> saveAnswers(
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable UUID consultationId,
            @Valid @RequestBody ChecklistAnswerRequest request
    ) {
        return ApiResponse.success(consultationChecklistService.saveAnswers(userId, consultationId, request));
    }

    @GetMapping
    @Operation(summary = "사용자 답변 기반 최종 준비물 조회")
    public ApiResponse<ResolvedChecklistResponse> getResolvedChecklist(
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @PathVariable UUID consultationId
    ) {
        return ApiResponse.success(
                consultationChecklistService.getResolvedChecklist(userId, consultationId)
        );
    }
}
