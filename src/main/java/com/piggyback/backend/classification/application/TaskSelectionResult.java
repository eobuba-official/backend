package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.TaskTypeView;

import java.util.UUID;

public record TaskSelectionResult(
        UUID consultationId,
        TaskTypeView task
) {
}
