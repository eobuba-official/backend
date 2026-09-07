package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.domain.TaskTypeCode;
import java.util.List;

public record ResolvedChecklistResponse(
        TaskTypeCode taskTypeCode,
        String taskTypeName,
        boolean resolved,
        List<ResolvedChecklistItemResponse> items
) {
    public ResolvedChecklistResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
