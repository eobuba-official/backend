package com.piggyback.backend.classification.port;

public interface TaskClassificationClient {
    LlmAnalysisOutput analyze(String utterance);
}
