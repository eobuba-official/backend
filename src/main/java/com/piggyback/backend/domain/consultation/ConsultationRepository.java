package com.piggyback.backend.domain.consultation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultationRepository extends JpaRepository<Consultation, String> {

    List<Consultation> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consultation c where c.id = :id and c.userId = :userId")
    Optional<Consultation> findByIdAndUserIdForUpdate(
            @Param("id") String id,
            @Param("userId") Long userId
    );
}
