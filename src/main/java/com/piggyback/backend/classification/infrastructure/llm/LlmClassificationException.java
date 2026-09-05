package com.piggyback.backend.classification.infrastructure.llm;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

public class LlmClassificationException extends BusinessException {

    public LlmClassificationException(String message) {
        super(ErrorCode.LLM_ERROR, message);
    }

    public LlmClassificationException(String message, Throwable cause) {
        super(ErrorCode.LLM_ERROR, message);
        initCause(cause);
    }
}
