package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.port.TaskClassificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskClassificationService {

    private static final Logger log = LoggerFactory.getLogger(TaskClassificationService.class);

    private final TaskClassificationClient client;
    private final ClassificationPolicy policy;

    public TaskClassificationService(
            TaskClassificationClient client,
            ClassificationPolicy policy
    ) {
        this.client = client;
        this.policy = policy;
    }

    public TaskClassificationOutcome classify(ClassificationCommand command) {
        var llmAnalysis = client.analyze(command.utterance());
        var classification = policy.normalize(command, llmAnalysis.classificationSignal());

        log.info(
                "Task classification completed: model={}, promptVersion={}, status={}, confidence={}",
                llmAnalysis.model(),
                llmAnalysis.promptVersion(),
                classification.status(),
                classification.confidence()
        );
        return new TaskClassificationOutcome(llmAnalysis, classification);
    }
}
