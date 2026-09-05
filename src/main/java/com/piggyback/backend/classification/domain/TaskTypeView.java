package com.piggyback.backend.classification.domain;

import com.piggyback.backend.domain.TaskTypeCode;

public record TaskTypeView(
        TaskTypeCode taskTypeCode,
        String name,
        String easyDescription
) {
    public static TaskTypeView from(TaskTypeCode code) {
        return new TaskTypeView(code, code.displayName(), code.easyDescription());
    }
}
