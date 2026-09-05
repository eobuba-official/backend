package com.piggyback.backend.repository;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.Branch;
import com.piggyback.backend.entity.BranchTask;
import com.piggyback.backend.entity.BranchTaskId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchTaskRepository extends JpaRepository<BranchTask, BranchTaskId> {

    @Query("select bt.branch from BranchTask bt where bt.id.taskTypeCode = :taskTypeCode")
    List<Branch> findBranchesByTaskTypeCode(@Param("taskTypeCode") TaskTypeCode taskTypeCode);
}
