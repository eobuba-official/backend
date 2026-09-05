package com.piggyback.backend.repository;

import com.piggyback.backend.entity.CongestionSlot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CongestionSlotRepository extends JpaRepository<CongestionSlot, Long> {

    @Query("""
            select cs from CongestionSlot cs
            join fetch cs.branch
            where cs.branch.id in :branchIds
              and cs.dayOfWeek in :daysOfWeek
            """)
    List<CongestionSlot> findForRecommendation(
            @Param("branchIds") Collection<Long> branchIds,
            @Param("daysOfWeek") Collection<Integer> daysOfWeek
    );
}
