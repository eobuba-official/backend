package com.piggyback.backend.classification.api;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.InputMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @Schema(
                description = "사용자가 말하거나 입력한 원문. 음성 문장을 수정했다면 수정된 문장을 전달합니다.",
                example = "통장을 잃어버렸는데 다시 만들고 싶어",
                maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "utterance는 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "utterance는 1,000자를 초과할 수 없습니다.")
        String utterance,

        @Schema(
                description = "입력 방식. CLOVA 또는 Web Speech 결과는 VOICE를 사용합니다.",
                example = "VOICE",
                allowableValues = {"VOICE", "TEXT"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "inputMethod는 필수입니다.")
        InputMethod inputMethod,

        @Schema(
                description = "STT 제공자가 전달한 인식 확신도. CLOVA CSR처럼 값이 없으면 null입니다.",
                example = "0.91",
                minimum = "0.0",
                maximum = "1.0",
                nullable = true
        )
        @DecimalMin(value = "0.0", message = "sttConfidence는 0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "sttConfidence는 1 이하여야 합니다.")
        Double sttConfidence
) {
    ClassificationCommand toCommand() {
        return new ClassificationCommand(utterance, inputMethod, sttConfidence);
    }
}
