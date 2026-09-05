package com.piggyback.backend.recommendation.repository;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ConsultationReferenceRepository {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockByIdAndUserId(UUID consultationId, Long userId) {
        return !entityManager.createNativeQuery("""
                        select id
                        from consultation
                        where id = :consultationId
                          and user_id = :userId
                        for update
                        """)
                .setParameter("consultationId", consultationId.toString())
                .setParameter("userId", userId)
                .getResultList()
                .isEmpty();
    }
}
