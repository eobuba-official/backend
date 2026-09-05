package com.piggyback.backend.classification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConsultationResultJpaRepository extends JpaRepository<ConsultationResultEntity, Long> {
}
