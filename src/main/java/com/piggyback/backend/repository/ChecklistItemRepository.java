package com.piggyback.backend.repository;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.ChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findByTaskTypeCodeOrderByDisplayOrderAsc(TaskTypeCode taskTypeCode);
}
