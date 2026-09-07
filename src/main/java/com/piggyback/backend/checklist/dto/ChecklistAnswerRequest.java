package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.checklist.domain.ChecklistConditionCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ChecklistAnswerRequest(
        @NotEmpty(message = "답변을 한 개 이상 입력해주세요.")
        List<@Valid Answer> answers
) {
    public record Answer(
            @NotNull(message = "조건 코드는 필수입니다.")
            ChecklistConditionCode conditionCode,
            @NotNull(message = "답변 값은 필수입니다.")
            Boolean value
    ) {
    }
}
