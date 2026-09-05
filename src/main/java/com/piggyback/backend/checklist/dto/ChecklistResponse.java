package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.domain.TaskTypeCode;
import java.util.List;

public record ChecklistResponse(
        TaskTypeCode taskTypeCode,
        String taskTypeName,
        List<ChecklistItemResponse> items
) {
    public ChecklistResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
