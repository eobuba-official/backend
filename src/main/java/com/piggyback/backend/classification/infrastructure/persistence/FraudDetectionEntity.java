package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.domain.FraudPatternType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fraud_detection")
class FraudDetectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", length = 36, nullable = false)
    private String consultationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", length = 20, nullable = false)
    private FraudPatternType patternType;

    @Column(name = "evidence", length = 500, nullable = false)
    private String evidence;

    @Column(name = "explanation", length = 500, nullable = false)
    private String explanation;

    protected FraudDetectionEntity() {
    }

    FraudDetectionEntity(
            String consultationId,
            FraudPatternType patternType,
            String evidence,
            String explanation
    ) {
        this.consultationId = consultationId;
        this.patternType = patternType;
        this.evidence = evidence;
        this.explanation = explanation;
    }

    String consultationId() {
        return consultationId;
    }

    FraudPatternType patternType() {
        return patternType;
    }

    String evidence() {
        return evidence;
    }

    String explanation() {
        return explanation;
    }
}
