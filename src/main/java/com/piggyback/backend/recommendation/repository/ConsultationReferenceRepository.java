package com.piggyback.backend.recommendation.repository;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsultationReferenceRepository {

    private final EntityManager entityManager;

    public boolean existsByIdAndUserId(UUID consultationId, Long userId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(*)
                        from consultation
                        where id = :consultationId
                          and user_id = :userId
                        """)
                .setParameter("consultationId", consultationId.toString())
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
