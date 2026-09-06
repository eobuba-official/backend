package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.VisitDecisionView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "후보 업무 선택 후 확정된 업무와 방문 판단")
public record SelectedTaskOutcome(
        @Schema(description = "상담 식별자")
        UUID consultationId,
        @Schema(description = "확정 후 상담 상태", example = "TASK_CONFIRMED")
        String status,
        @Schema(description = "사용자가 선택해 확정한 업무")
        TaskTypeView task,
        @Schema(description = "확정 업무의 DB 규칙 기반 방문 판단")
        VisitDecisionView visitDecision
) {
}
