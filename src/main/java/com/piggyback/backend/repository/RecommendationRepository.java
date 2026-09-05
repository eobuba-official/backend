package com.piggyback.backend.repository;

import com.piggyback.backend.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from Recommendation r where r.consultationId = :consultationId")
    void deleteByConsultationId(@Param("consultationId") String consultationId);
}
