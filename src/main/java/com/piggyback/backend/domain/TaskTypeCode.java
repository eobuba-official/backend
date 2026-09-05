package com.piggyback.backend.domain;

import java.util.Locale;
import java.util.Optional;

public enum TaskTypeCode {
    PASSBOOK_REISSUE("통장 재발급", "통장을 잃어버렸을 때 새로 만드는 일"),
    PROXY_TASK("대리 업무", "가족 일을 대신 처리하는 것"),
    DEPOSIT_EARLY_CLOSE("예금 중도해지", "만기 전에 돈을 찾는 것"),
    CARD_REISSUE("카드 재발급", "카드를 새로 받는 일"),
    PASSWORD_CHANGE("비밀번호 변경", "비밀번호를 바꾸거나 찾는 일"),
    AUTO_TRANSFER_CHANGE("자동이체 변경", "매달 자동으로 나가는 돈을 바꾸는 것"),
    BALANCE_INQUIRY("잔액·거래내역 조회", "통장에 얼마 있는지 보는 일"),
    ACCOUNT_TRANSFER("계좌이체", "다른 사람에게 돈을 보내는 일");

    private final String displayName;
    private final String easyDescription;

    TaskTypeCode(String displayName, String easyDescription) {
        this.displayName = displayName;
        this.easyDescription = easyDescription;
    }

    public String displayName() {
        return displayName;
    }

    public String easyDescription() {
        return easyDescription;
    }

    public static Optional<TaskTypeCode> fromExternalValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
