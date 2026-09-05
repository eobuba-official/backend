package com.piggyback.backend.recommendation.exception;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

public class ConsultationNotFoundException extends BusinessException {

    public ConsultationNotFoundException() {
        super(ErrorCode.CONSULTATION_NOT_FOUND);
    }
}
