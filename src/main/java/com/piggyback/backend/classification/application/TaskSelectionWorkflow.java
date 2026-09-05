package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.visit.service.VisitDecisionService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskSelectionWorkflow {

    private final TaskSelectionService selectionService;
    private final VisitDecisionService visitDecisionService;

    public TaskSelectionWorkflow(
            TaskSelectionService selectionService,
            VisitDecisionService visitDecisionService
    ) {
        this.selectionService = selectionService;
        this.visitDecisionService = visitDecisionService;
    }

    @Transactional
    public SelectedTaskOutcome select(long userId, UUID consultationId, String taskTypeCode) {
        var selection = selectionService.select(userId, consultationId, taskTypeCode);
        var visitDecision = visitDecisionService.decide(selection.task().taskTypeCode());
        return SelectedTaskOutcome.confirmed(selection, VisitDecisionView.from(visitDecision));
    }
}
