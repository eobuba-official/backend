package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.classification.port.VisitDecisionView;
import com.piggyback.backend.visit.service.VisitDecisionService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TaskClassificationWorkflow {

    private final TaskClassificationService classificationService;
    private final ClassificationResultStore resultStore;
    private final VisitDecisionService visitDecisionService;

    public TaskClassificationWorkflow(
            TaskClassificationService classificationService,
            ClassificationResultStore resultStore,
            VisitDecisionService visitDecisionService
    ) {
        this.classificationService = classificationService;
        this.resultStore = resultStore;
        this.visitDecisionService = visitDecisionService;
    }

    @Transactional
    public AnalyzeResult analyze(long userId, ClassificationCommand command) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        var outcome = classificationService.classify(command);
        if (outcome.llmAnalysis().fraudDetected()) {
            var consultationId = resultStore.saveSuspended(
                    userId,
                    command,
                    outcome.classification()
            );
            return AnalyzeResult.suspended(
                    consultationId,
                    outcome.classification(),
                    validFraudPatterns(command.utterance(), outcome.llmAnalysis().fraudPatterns())
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

    private List<LlmFraudPattern> validFraudPatterns(
            String utterance,
            List<LlmFraudPattern> patterns
    ) {
        return patterns.stream()
                .filter(Objects::nonNull)
                .filter(pattern -> FraudPatternType.fromExternalValue(pattern.type()).isPresent())
                .filter(pattern -> pattern.evidence() != null && !pattern.evidence().isBlank())
                .filter(pattern -> utterance.contains(pattern.evidence()))
                .toList();
    }
}
