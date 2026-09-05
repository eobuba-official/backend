package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskClassificationServiceTest {

    @Test
    void sendsOnlyUtteranceToClientAndReturnsNormalizedResult() {
        AtomicReference<String> capturedUtterance = new AtomicReference<>();
        TaskClassificationClient client = utterance -> {
            capturedUtterance.set(utterance);
            return new LlmAnalysisOutput(
                    "test-model",
                    "test-prompt",
                    "자동이체를 바꿔줘",
                    false,
                    List.of(),
                    "AUTO_TRANSFER_CHANGE",
                    0.9,
                    List.of()
            );
        };
        var properties = new ClassificationProperties();
        var service = new TaskClassificationService(client, new ClassificationPolicy(properties));
        var command = new ClassificationCommand("자동인체를 바꿔줘", InputMethod.TEXT, null);

        var outcome = service.classify(command);

        assertEquals("자동인체를 바꿔줘", capturedUtterance.get());
        assertEquals("AUTO_TRANSFER_CHANGE", outcome.classification().task().taskTypeCode().name());
        assertEquals("자동이체를 바꿔줘", outcome.classification().correctedUtterance());
        assertFalse(outcome.llmAnalysis().fraudDetected());
    }
}
