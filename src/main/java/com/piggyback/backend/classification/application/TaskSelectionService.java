package com.piggyback.backend.classification.application;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskSelectionService {

    private final ClassificationResultStore resultStore;

    public TaskSelectionService(ClassificationResultStore resultStore) {
        this.resultStore = resultStore;
    }

    public TaskSelectionResult select(long userId, UUID consultationId, String taskTypeCode) {
        if (userId <= 0 || consultationId == null) {
            throw new IllegalArgumentException("userId and consultationId are required");
        }

        var selectedTask = TaskTypeCode.fromExternalValue(taskTypeCode)
                .orElseThrow(() -> new TaskSelectionException(
                        TaskSelectionException.Reason.TASK_TYPE_NOT_FOUND,
                        "선택할 수 없는 업무 코드입니다."
                ));

        var outcome = resultStore.confirmCandidate(userId, consultationId, selectedTask);
        switch (outcome) {
            case CONFIRMED -> {
                return new TaskSelectionResult(consultationId, TaskTypeView.from(selectedTask));
            }
            case CONSULTATION_NOT_FOUND -> throw new TaskSelectionException(
                    TaskSelectionException.Reason.CONSULTATION_NOT_FOUND,
                    "상담을 찾을 수 없습니다."
            );
            case INVALID_STATE -> throw new TaskSelectionException(
                    TaskSelectionException.Reason.INVALID_STATE,
                    "후보 선택이 가능한 상담 상태가 아닙니다."
            );
            case TASK_NOT_CANDIDATE -> throw new TaskSelectionException(
                    TaskSelectionException.Reason.TASK_TYPE_NOT_FOUND,
                    "제시된 후보에 없는 업무 코드입니다."
            );
            default -> throw new IllegalStateException("Unexpected selection outcome: " + outcome);
        }
    }
}
