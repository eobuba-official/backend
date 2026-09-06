package com.piggyback.backend.classification.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TaskSelectionRequest(
        @Schema(
                description = "분석 응답의 candidates에 포함된 8종 업무 코드 중 하나",
                example = "PASSBOOK_REISSUE",
                allowableValues = {
                        "PASSBOOK_REISSUE",
                        "PROXY_TASK",
                        "DEPOSIT_EARLY_CLOSE",
                        "CARD_REISSUE",
                        "PASSWORD_CHANGE",
                        "AUTO_TRANSFER_CHANGE",
                        "BALANCE_INQUIRY",
                        "ACCOUNT_TRANSFER"
                },
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank String taskTypeCode
) {
}
