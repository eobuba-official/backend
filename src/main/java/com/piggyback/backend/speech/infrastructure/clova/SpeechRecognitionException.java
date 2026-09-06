package com.piggyback.backend.speech.infrastructure.clova;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

public class SpeechRecognitionException extends BusinessException {

    public SpeechRecognitionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SpeechRecognitionException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message);
        initCause(cause);
    }
}
