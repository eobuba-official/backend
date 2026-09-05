package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.domain.TaskTypeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consultation_candidate")
class ConsultationCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", length = 36, nullable = false)
    private String consultationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type_code", length = 40, nullable = false)
    private TaskTypeCode taskTypeCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ConsultationCandidateEntity() {
    }

    ConsultationCandidateEntity(String consultationId, TaskTypeCode taskTypeCode, int displayOrder) {
        this.consultationId = consultationId;
        this.taskTypeCode = taskTypeCode;
        this.displayOrder = displayOrder;
    }

    String consultationId() {
        return consultationId;
    }

    TaskTypeCode taskTypeCode() {
        return taskTypeCode;
    }

    int displayOrder() {
        return displayOrder;
    }
}
