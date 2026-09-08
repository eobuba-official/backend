package com.piggyback.backend.classification.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CorrectionConfirmationRequest(
        @Schema(
                description = "Gemini 보정 문장을 승인하거나 사용자가 직접 수정해 확정한 문장",
                example = "통장을 잃어버려서 다시 만들고 싶어",
                maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "confirmedUtterance는 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "confirmedUtterance는 1,000자를 초과할 수 없습니다.")
        String confirmedUtterance,

        @Schema(
                description = "사용자가 직접 확정한 업무 코드. 생략하면 확정 문장을 다시 분류합니다.",
                example = "PASSBOOK_REISSUE",
                nullable = true,
                allowableValues = {
                        "PASSBOOK_REISSUE",
                        "PROXY_TASK",
                        "DEPOSIT_EARLY_CLOSE",
                        "CARD_REISSUE",
                        "PASSWORD_CHANGE",
                        "AUTO_TRANSFER_CHANGE",
                        "BALANCE_INQUIRY",
                        "ACCOUNT_TRANSFER"
                }
        )
        String taskTypeCode
) {
}
