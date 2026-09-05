package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.domain.ClassificationStatus;
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
@Table(name = "consultation_result")
class ConsultationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", length = 36, nullable = false, unique = true)
    private String consultationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type_code", length = 40)
    private TaskTypeCode taskTypeCode;

    @Column(name = "confidence")
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", length = 20, nullable = false)
    private ClassificationStatus classificationStatus;

    protected ConsultationResultEntity() {
    }

    ConsultationResultEntity(
            String consultationId,
            TaskTypeCode taskTypeCode,
            double confidence,
            ClassificationStatus classificationStatus
    ) {
        this.consultationId = consultationId;
        this.taskTypeCode = taskTypeCode;
        this.confidence = confidence;
        this.classificationStatus = classificationStatus;
    }
}
