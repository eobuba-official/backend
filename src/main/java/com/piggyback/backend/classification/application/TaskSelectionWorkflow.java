package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.visit.service.VisitDecisionService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskSelectionWorkflow {

    private final ClassificationResultStore resultStore;
    private final VisitDecisionService visitDecisionService;

    public TaskSelectionWorkflow(
            ClassificationResultStore resultStore,
            VisitDecisionService visitDecisionService
    ) {
        this.resultStore = resultStore;
        this.visitDecisionService = visitDecisionService;
    }

    @Transactional
    public SelectedTaskOutcome select(long userId, UUID consultationId, String taskTypeCode) {
        if (userId <= 0 || consultationId == null) {
            throw new IllegalArgumentException("userId and consultationId are required");
        }

        TaskTypeCode selectedTask = TaskTypeCode.fromExternalValue(taskTypeCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TASK_TYPE_NOT_FOUND,
                        "선택할 수 없는 업무 코드입니다."
                ));
        switch (resultStore.confirmCandidate(userId, consultationId, selectedTask)) {
            case CONFIRMED -> {
            }
            case CONSULTATION_NOT_FOUND -> throw new BusinessException(
                    ErrorCode.CONSULTATION_NOT_FOUND,
                    "상담을 찾을 수 없습니다."
            );
            case INVALID_STATE -> throw new BusinessException(
                    ErrorCode.INVALID_STATE,
                    "후보 선택이 가능한 상담 상태가 아닙니다."
            );
            case TASK_NOT_CANDIDATE -> throw new BusinessException(
                    ErrorCode.TASK_TYPE_NOT_FOUND,
                    "제시된 후보에 없는 업무 코드입니다."
            );
        }

        TaskTypeView task = TaskTypeView.from(selectedTask);
        var visitDecision = visitDecisionService.decide(selectedTask);
        return new SelectedTaskOutcome(
                consultationId,
                "TASK_CONFIRMED",
                task,
                VisitDecisionView.from(visitDecision)
        );
    }
}
