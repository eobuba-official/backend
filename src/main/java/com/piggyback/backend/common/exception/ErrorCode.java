package com.piggyback.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_SMS_CODE(HttpStatus.UNAUTHORIZED, "인증번호가 올바르지 않습니다."),
    SMS_REQUEST_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 인증번호를 요청해주세요."),
    CONSULTATION_NOT_FOUND(HttpStatus.NOT_FOUND, "상담을 찾을 수 없습니다."),
    TASK_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "업무 유형을 찾을 수 없습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없는 요청입니다."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
    NO_WARNING_TO_DISMISS(HttpStatus.CONFLICT, "해제할 사기 경고가 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    LLM_ERROR(HttpStatus.BAD_GATEWAY, "분석 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
