package com.piggyback.backend.classification.application;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

public class TaskSelectionException extends BusinessException {

    private final Reason reason;

    public TaskSelectionException(Reason reason, String message) {
        super(toErrorCode(reason), message);
        this.reason = reason;
    }

    private static ErrorCode toErrorCode(Reason reason) {
        return switch (reason) {
            case CONSULTATION_NOT_FOUND -> ErrorCode.CONSULTATION_NOT_FOUND;
            case TASK_TYPE_NOT_FOUND -> ErrorCode.TASK_TYPE_NOT_FOUND;
            case INVALID_STATE -> ErrorCode.INVALID_STATE;
        };
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        CONSULTATION_NOT_FOUND,
        TASK_TYPE_NOT_FOUND,
        INVALID_STATE
    }
}
