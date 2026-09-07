package com.piggyback.backend.entity;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.checklist.domain.ChecklistConditionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "checklist_item",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CHECKLIST_ITEM",
                columnNames = {"task_type_code", "item_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type_code", length = 40, nullable = false)
    private TaskTypeCode taskTypeCode;

    @Column(name = "item_code", length = 30, nullable = false)
    private String itemCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "easy_description", length = 300, nullable = false)
    private String easyDescription;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "item_condition", length = 300)
    private String itemCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_code", length = 40)
    private ChecklistConditionCode conditionCode;

    @Column(name = "expected_answer")
    private Boolean expectedAnswer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ChecklistItem(
            TaskTypeCode taskTypeCode,
            String itemCode,
            String name,
            String easyDescription,
            boolean required,
            String itemCondition,
            int displayOrder
    ) {
        this(taskTypeCode, itemCode, name, easyDescription, required, itemCondition, null, null, displayOrder);
    }

    public ChecklistItem(
            TaskTypeCode taskTypeCode,
            String itemCode,
            String name,
            String easyDescription,
            boolean required,
            String itemCondition,
            ChecklistConditionCode conditionCode,
            Boolean expectedAnswer,
            int displayOrder
    ) {
        this.taskTypeCode = taskTypeCode;
        this.itemCode = itemCode;
        this.name = name;
        this.easyDescription = easyDescription;
        this.required = required;
        this.itemCondition = itemCondition;
        this.conditionCode = conditionCode;
        this.expectedAnswer = expectedAnswer;
        this.displayOrder = displayOrder;
    }
}
