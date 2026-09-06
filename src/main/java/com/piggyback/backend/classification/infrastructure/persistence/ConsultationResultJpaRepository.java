package com.piggyback.backend.classification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ConsultationResultJpaRepository extends JpaRepository<ConsultationResultEntity, Long> {

    Optional<ConsultationResultEntity> findByConsultationId(String consultationId);
}
