package com.piggyback.backend.repository;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.TaskVisitRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskVisitRuleRepository extends JpaRepository<TaskVisitRule, TaskTypeCode> {
}
