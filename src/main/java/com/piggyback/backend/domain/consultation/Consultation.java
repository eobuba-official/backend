package com.piggyback.backend.domain.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "consultation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String utterance;

    @Column(name = "corrected_utterance", nullable = false, length = 1000)
    private String correctedUtterance;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_method", nullable = false, length = 10)
    private InputMethod inputMethod;

    @Column(name = "stt_confidence", precision = 3, scale = 2)
    private BigDecimal sttConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsultationStatus status;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "task_type_code", length = 40)
    private String taskTypeCode;

    @Column(name = "warning_dismissed_at")
    private LocalDateTime warningDismissedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Consultation(String id, Long userId, String utterance, String correctedUtterance,
                         InputMethod inputMethod, BigDecimal sttConfidence, ConsultationStatus status,
                         BigDecimal confidence, String taskTypeCode) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.userId = userId;
        this.utterance = utterance;
        this.correctedUtterance = correctedUtterance;
        this.inputMethod = inputMethod;
        this.sttConfidence = sttConfidence;
        this.status = status;
        this.confidence = confidence;
        this.taskTypeCode = taskTypeCode;
        this.createdAt = LocalDateTime.now();
    }
}
