package com.piggyback.backend.repository;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTypeRepository extends JpaRepository<TaskType, TaskTypeCode> {
}
