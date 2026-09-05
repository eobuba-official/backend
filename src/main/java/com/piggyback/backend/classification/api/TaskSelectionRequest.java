package com.piggyback.backend.classification.api;

import jakarta.validation.constraints.NotBlank;

public record TaskSelectionRequest(
        @NotBlank String taskTypeCode
) {
}
