package com.piggyback.backend.exception;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

public class TaskTypeNotFoundException extends BusinessException {

    public TaskTypeNotFoundException() {
        super(ErrorCode.TASK_TYPE_NOT_FOUND);
    }
}
