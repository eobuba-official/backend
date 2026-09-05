package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.domain.ConsultationStatus;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.domain.TaskTypeCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultation")
class ConsultationEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "utterance", length = 1000, nullable = false)
    private String utterance;

    @Column(name = "corrected_utterance", length = 1000, nullable = false)
    private String correctedUtterance;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_method", length = 10, nullable = false)
    private InputMethod inputMethod;

    @Column(name = "stt_confidence")
    private Double sttConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ConsultationStatus status;

    @Column(name = "confidence")
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type_code", length = 40)
    private TaskTypeCode taskTypeCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ConsultationEntity() {
    }

    ConsultationEntity(
            UUID id,
            long userId,
            String utterance,
            String correctedUtterance,
            InputMethod inputMethod,
            Double sttConfidence,
            ConsultationStatus status,
            double confidence,
            TaskTypeCode taskTypeCode,
            LocalDateTime createdAt
    ) {
        this.id = id.toString();
        this.userId = userId;
        this.utterance = utterance;
        this.correctedUtterance = correctedUtterance;
        this.inputMethod = inputMethod;
        this.sttConfidence = sttConfidence;
        this.status = status;
        this.confidence = confidence;
        this.taskTypeCode = taskTypeCode;
        this.createdAt = createdAt;
    }

    String id() {
        return id;
    }

    ConsultationStatus status() {
        return status;
    }

    Long userId() {
        return userId;
    }

    String correctedUtterance() {
        return correctedUtterance;
    }

    TaskTypeCode taskTypeCode() {
        return taskTypeCode;
    }

    void confirm(TaskTypeCode selectedTask) {
        status = ConsultationStatus.TASK_CONFIRMED;
        taskTypeCode = selectedTask;
    }
}
