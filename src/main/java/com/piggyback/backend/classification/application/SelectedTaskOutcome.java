package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.VisitDecisionView;

import java.util.UUID;

public record SelectedTaskOutcome(
        UUID consultationId,
        String status,
        TaskTypeView task,
        VisitDecisionView visitDecision
) {
    public static SelectedTaskOutcome confirmed(
            TaskSelectionResult selection,
            VisitDecisionView visitDecision
    ) {
        return new SelectedTaskOutcome(
                selection.consultationId(),
                "TASK_CONFIRMED",
                selection.task(),
                visitDecision
        );
    }
}
