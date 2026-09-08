package com.piggyback.backend.classification.port;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.domain.TaskTypeCode;

import java.util.List;
import java.util.UUID;

public interface ClassificationResultStore {

    UUID save(long userId, ClassificationCommand command, ClassificationResult result);

    UUID saveSuspended(
            long userId,
            ClassificationCommand command,
            ClassificationResult pendingResult,
            List<ValidatedFraudPattern> fraudPatterns
    );

    UUID saveAwaitingCorrectionConfirmation(
            long userId,
            ClassificationCommand command,
            ClassificationResult pendingResult
    );

    ConfirmationContext findCorrectionConfirmation(long userId, UUID consultationId);

    ConfirmationOutcome completeCorrectionConfirmation(
            long userId,
            UUID consultationId,
            String confirmedUtterance,
            ClassificationResult result,
            List<ValidatedFraudPattern> fraudPatterns
    );

    SelectionOutcome confirmCandidate(long userId, UUID consultationId, TaskTypeCode selectedTask);

    enum SelectionOutcome {
        CONFIRMED,
        CONSULTATION_NOT_FOUND,
        INVALID_STATE,
        TASK_NOT_CANDIDATE
    }

    record ConfirmationContext(ConfirmationOutcome outcome, String originalUtterance) {
        public static ConfirmationContext ready(String originalUtterance) {
            return new ConfirmationContext(ConfirmationOutcome.READY, originalUtterance);
        }

        public static ConfirmationContext of(ConfirmationOutcome outcome) {
            return new ConfirmationContext(outcome, null);
        }
    }

    enum ConfirmationOutcome {
        READY,
        COMPLETED,
        CONSULTATION_NOT_FOUND,
        INVALID_STATE
    }
}
