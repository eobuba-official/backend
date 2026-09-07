package com.piggyback.backend.tasktype.service;

import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.repository.TaskTypeRepository;
import com.piggyback.backend.tasktype.dto.TaskTypeListResponse;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskTypeService {

    private final TaskTypeRepository taskTypeRepository;

    @Transactional(readOnly = true)
    public TaskTypeListResponse getTaskTypes() {
        List<TaskType> taskTypes = taskTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(taskType -> taskType.getCode().ordinal()))
                .toList();
        return TaskTypeListResponse.from(taskTypes);
    }
}
