package com.piggyback.backend.classification.domain;

import com.piggyback.backend.domain.TaskTypeCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record TaskTypeView(
        @Schema(description = "8종 은행 업무 코드", example = "PASSBOOK_REISSUE")
        TaskTypeCode taskTypeCode,
        @Schema(description = "업무명", example = "통장 재발급")
        String name,
        @Schema(description = "시니어에게 표시할 쉬운 설명")
        String easyDescription
) {
    public static TaskTypeView from(TaskTypeCode code) {
        return new TaskTypeView(code, code.displayName(), code.easyDescription());
    }
}
