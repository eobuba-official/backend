package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.visit.service.VisitDecisionService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskClassificationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(TaskClassificationWorkflow.class);

    private final TaskClassificationClient classificationClient;
    private final ClassificationPolicy classificationPolicy;
    private final FraudDetectionPolicy fraudDetectionPolicy;
    private final ClassificationResultStore resultStore;
    private final VisitDecisionService visitDecisionService;

    public TaskClassificationWorkflow(
            TaskClassificationClient classificationClient,
            ClassificationPolicy classificationPolicy,
            FraudDetectionPolicy fraudDetectionPolicy,
            ClassificationResultStore resultStore,
            VisitDecisionService visitDecisionService
    ) {
        this.classificationClient = classificationClient;
        this.classificationPolicy = classificationPolicy;
        this.fraudDetectionPolicy = fraudDetectionPolicy;
        this.resultStore = resultStore;
        this.visitDecisionService = visitDecisionService;
    }

    @Transactional
    public AnalyzeResult analyze(long userId, ClassificationCommand command) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        var llmAnalysis = classificationClient.analyze(command.utterance());
        var classification = classificationPolicy.normalize(command, llmAnalysis.classificationSignal());
        log.info(
                "Task classification completed: model={}, promptVersion={}, status={}, confidence={}",
                llmAnalysis.model(),
                llmAnalysis.promptVersion(),
                classification.status(),
                classification.confidence()
        );

        var fraudPatterns = fraudDetectionPolicy.evaluate(command.utterance(), llmAnalysis);
        if (!fraudPatterns.isEmpty()) {
            var consultationId = resultStore.saveSuspended(
                    userId,
                    command,
                    classification,
                    fraudPatterns
            );
            return AnalyzeResult.suspended(
                    consultationId,
                    classification,
                    fraudPatterns
            );
        }
        var consultationId = resultStore.save(userId, command, classification);
        VisitDecisionView visitDecision = classification.task() == null
                ? null
                : VisitDecisionView.from(visitDecisionService.decide(
                        classification.task().taskTypeCode()
                ));
        return AnalyzeResult.normal(consultationId, classification, visitDecision);
    }
}
