package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.domain.TaskTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ConsultationCandidateJpaRepository extends JpaRepository<ConsultationCandidateEntity, Long> {

    boolean existsByConsultationIdAndTaskTypeCode(String consultationId, TaskTypeCode taskTypeCode);

    List<ConsultationCandidateEntity> findAllByConsultationIdOrderByDisplayOrder(String consultationId);
}
