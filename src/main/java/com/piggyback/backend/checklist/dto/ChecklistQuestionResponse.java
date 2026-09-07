package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.checklist.domain.ChecklistConditionCode;

public record ChecklistQuestionResponse(
        ChecklistConditionCode conditionCode,
        String question,
        String answerType,
        boolean answered,
        Boolean answer
) {
}
