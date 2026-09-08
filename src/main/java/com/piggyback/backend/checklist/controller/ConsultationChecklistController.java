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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "준비물", description = "업무별 준비물과 사용자 답변을 반영한 최종 준비물을 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class ConsultationChecklistController {

    private final ConsultationChecklistService consultationChecklistService;

    @GetMapping("/questions")
    @Operation(
            summary = "조건부 준비물 질문 조회",
            description = "상담에서 확정된 업무의 조건부 준비물을 판정하기 위한 질문을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조건 질문 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담 또는 확정된 업무가 없음")
    })
    public ApiResponse<ChecklistQuestionsResponse> getQuestions(
            @Parameter(hidden = true)
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @Parameter(description = "분석 응답에서 받은 상담 UUID", required = true)
            @PathVariable UUID consultationId
    ) {
        return ApiResponse.success(consultationChecklistService.getQuestions(userId, consultationId));
    }

    @PutMapping("/answers")
    @Operation(
            summary = "조건부 준비물 답변 저장·수정",
            description = "조건 질문에 대한 사용자 답변을 저장합니다. 같은 질문의 답변을 다시 보내면 기존 답변을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답변 저장 또는 수정 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "질문 코드 또는 답변 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담 또는 확정된 업무가 없음")
    })
    public ApiResponse<ChecklistAnswerResponse> saveAnswers(
            @Parameter(hidden = true)
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @Parameter(description = "분석 응답에서 받은 상담 UUID", required = true)
            @PathVariable UUID consultationId,
            @Valid @RequestBody ChecklistAnswerRequest request
    ) {
        return ApiResponse.success(consultationChecklistService.saveAnswers(userId, consultationId, request));
    }

    @GetMapping
    @Operation(
            summary = "최종 준비물 조회",
            description = "필수 준비물은 항상 포함하고, 조건부 준비물은 저장된 사용자 답변이 조건과 일치할 때만 포함합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "최종 준비물 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상담 또는 확정된 업무가 없음")
    })
    public ApiResponse<ResolvedChecklistResponse> getResolvedChecklist(
            @Parameter(hidden = true)
            @RequestAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE) Long userId,
            @Parameter(description = "분석 응답에서 받은 상담 UUID", required = true)
            @PathVariable UUID consultationId
    ) {
        return ApiResponse.success(
                consultationChecklistService.getResolvedChecklist(userId, consultationId)
        );
    }
}
