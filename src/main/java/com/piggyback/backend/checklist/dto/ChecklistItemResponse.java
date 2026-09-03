package com.piggyback.backend.checklist.dto;

import com.piggyback.backend.entity.ChecklistItem;

public record ChecklistItemResponse(
        String itemCode,
        String name,
        String easyDescription,
        boolean required,
        String condition,
        int displayOrder
) {
    public static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.getItemCode(),
                item.getName(),
                item.getEasyDescription(),
                item.isRequired(),
                item.isRequired() ? null : item.getItemCondition(),
                item.getDisplayOrder()
        );
    }
}
