package com.piggyback.backend.checklist.dto;

import java.util.List;

public record ChecklistQuestionsResponse(List<ChecklistQuestionResponse> questions) {
    public ChecklistQuestionsResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
