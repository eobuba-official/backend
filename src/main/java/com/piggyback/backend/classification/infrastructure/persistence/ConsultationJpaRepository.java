package com.piggyback.backend.classification.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ConsultationJpaRepository extends JpaRepository<ConsultationEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsultationEntity c where c.id = :id and c.userId = :userId")
    Optional<ConsultationEntity> findOwnedForUpdate(
            @Param("id") String id,
            @Param("userId") long userId
    );
}
