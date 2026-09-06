package com.piggyback.backend.classification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface FraudDetectionJpaRepository extends JpaRepository<FraudDetectionEntity, Long> {

    List<FraudDetectionEntity> findAllByConsultationIdOrderById(String consultationId);
}
