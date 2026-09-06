package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.visit.service.VisitDecisionService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class TaskClassificationWorkflow {

    private final TaskClassificationService classificationService;
    private final FraudDetectionPolicy fraudDetectionPolicy;
    private final ClassificationResultStore resultStore;
    private final VisitDecisionService visitDecisionService;

    public TaskClassificationWorkflow(
            TaskClassificationService classificationService,
            FraudDetectionPolicy fraudDetectionPolicy,
            ClassificationResultStore resultStore,
            VisitDecisionService visitDecisionService
    ) {
        this.classificationService = classificationService;
        this.fraudDetectionPolicy = fraudDetectionPolicy;
        this.resultStore = resultStore;
        this.visitDecisionService = visitDecisionService;
    }

    @Transactional
    public AnalyzeResult analyze(long userId, ClassificationCommand command) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        var outcome = classificationService.classify(command);
        var fraudAssessment = fraudDetectionPolicy.evaluate(command.utterance(), outcome.llmAnalysis());
        if (fraudAssessment.detected()) {
            var consultationId = resultStore.saveSuspended(
                    userId,
                    command,
                    outcome.classification(),
                    fraudAssessment.patterns()
            );
            return AnalyzeResult.suspended(
                    consultationId,
                    outcome.classification(),
                    fraudAssessment.patterns()
            );
        }
        var consultationId = resultStore.save(userId, command, outcome.classification());
        VisitDecisionView visitDecision = outcome.classification().task() == null
                ? null
                : VisitDecisionView.from(visitDecisionService.decide(
                        outcome.classification().task().taskTypeCode()
                ));
        return AnalyzeResult.normal(consultationId, outcome.classification(), visitDecision);
    }
}
