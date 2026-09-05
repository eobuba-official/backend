package com.piggyback.backend.domain.consultation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRepository extends JpaRepository<Consultation, String> {

    List<Consultation> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
