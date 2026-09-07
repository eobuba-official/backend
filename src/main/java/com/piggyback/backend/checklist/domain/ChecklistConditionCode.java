package com.piggyback.backend.checklist.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChecklistConditionCode {

    IS_PROXY("다른 사람이 대신 방문하나요?"),
    USES_SEAL("통장이나 예금을 도장으로 만들었나요?"),
    HAS_PASSBOOK("해지할 예금 통장을 가지고 있나요?"),
    IS_PASSBOOK_PASSWORD_CHANGE("통장 비밀번호를 바꾸려는 건가요?");

    private final String question;
}
