package com.piggyback.backend.checklist.service;

import com.piggyback.backend.checklist.dto.ChecklistItemResponse;
import com.piggyback.backend.checklist.dto.ChecklistResponse;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final TaskTypeRepository taskTypeRepository;
    private final ChecklistItemRepository checklistItemRepository;

    public ChecklistResponse getChecklist(TaskTypeCode taskTypeCode) {
        TaskType taskType = taskTypeRepository.findById(taskTypeCode)
                .orElseThrow(TaskTypeNotFoundException::new);

        List<ChecklistItemResponse> items = checklistItemRepository
                .findByTaskTypeCodeOrderByDisplayOrderAsc(taskTypeCode)
                .stream()
                .map(ChecklistItemResponse::from)
                .toList();

        return new ChecklistResponse(taskType.getCode(), taskType.getName(), items);
    }
}
