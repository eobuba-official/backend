package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.InputMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @NotBlank(message = "utterance는 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "utterance는 1,000자를 초과할 수 없습니다.")
        String utterance,

        @NotNull(message = "inputMethod는 필수입니다.")
        InputMethod inputMethod,

        @DecimalMin(value = "0.0", message = "sttConfidence는 0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "sttConfidence는 1 이하여야 합니다.")
        Double sttConfidence
) {
    ClassificationCommand toCommand() {
        return new ClassificationCommand(utterance, inputMethod, sttConfidence);
    }
}
