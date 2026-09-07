package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.checklist.domain.ChecklistItemStatus;

public record ResolvedChecklistItemResponse(
        String itemCode,
        String name,
        String easyDescription,
        boolean required,
        ChecklistItemStatus status,
        String reason,
        int displayOrder
) {
}
