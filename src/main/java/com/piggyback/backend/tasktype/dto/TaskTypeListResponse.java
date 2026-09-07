package com.piggyback.backend.tasktype.dto;

import com.piggyback.backend.entity.TaskType;
import java.util.List;

public record TaskTypeListResponse(List<TaskTypeItem> taskTypes) {

    public record TaskTypeItem(String code, String name, String easyDescription) {

        public static TaskTypeItem from(TaskType taskType) {
            return new TaskTypeItem(taskType.getCode().name(), taskType.getName(), taskType.getEasyDescription());
        }
    }

    public static TaskTypeListResponse from(List<TaskType> taskTypes) {
        return new TaskTypeListResponse(taskTypes.stream().map(TaskTypeItem::from).toList());
    }
}
