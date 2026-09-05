package com.piggyback.backend.classification.port;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.domain.TaskTypeCode;

import java.util.UUID;

public interface ClassificationResultStore {

    UUID save(long userId, ClassificationCommand command, ClassificationResult result);

    UUID saveSuspended(long userId, ClassificationCommand command, ClassificationResult pendingResult);

    SelectionOutcome confirmCandidate(long userId, UUID consultationId, TaskTypeCode selectedTask);

    enum SelectionOutcome {
        CONFIRMED,
        CONSULTATION_NOT_FOUND,
        INVALID_STATE,
        TASK_NOT_CANDIDATE
    }
}
