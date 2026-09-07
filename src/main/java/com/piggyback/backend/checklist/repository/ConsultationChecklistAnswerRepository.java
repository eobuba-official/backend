package com.piggyback.backend.checklist.repository;

import com.piggyback.backend.checklist.domain.ChecklistConditionCode;
import com.piggyback.backend.checklist.entity.ConsultationChecklistAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationChecklistAnswerRepository
        extends JpaRepository<ConsultationChecklistAnswer, Long> {

    List<ConsultationChecklistAnswer> findAllByConsultationId(String consultationId);

    Optional<ConsultationChecklistAnswer> findByConsultationIdAndConditionCode(
            String consultationId,
            ChecklistConditionCode conditionCode
    );
}
