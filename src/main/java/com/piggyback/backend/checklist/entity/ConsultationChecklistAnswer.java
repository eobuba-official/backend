package com.piggyback.backend.checklist.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "consultation_checklist_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CONSULTATION_CHECKLIST_ANSWER",
                columnNames = {"consultation_id", "condition_code"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationChecklistAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", length = 36, nullable = false)
    private String consultationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_code", length = 40, nullable = false)
    private ChecklistConditionCode conditionCode;

    @Column(name = "answer_value", nullable = false)
    private boolean answerValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ConsultationChecklistAnswer(
            String consultationId,
            ChecklistConditionCode conditionCode,
            boolean answerValue
    ) {
        this.consultationId = consultationId;
        this.conditionCode = conditionCode;
        this.answerValue = answerValue;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(boolean answerValue) {
        this.answerValue = answerValue;
        this.updatedAt = LocalDateTime.now();
    }
}
