package com.piggyback.backend.classification.domain;

import java.util.Locale;
import java.util.Optional;

public enum FraudPatternType {
    IMPERSONATION("기관 사칭", "검찰·경찰·금융감독원·은행 등 기관이나 직원을 사칭했습니다."),
    SAFE_ACCOUNT("안전계좌 요구", "은행과 공공기관은 자금 보호를 이유로 다른 계좌 송금을 요구하지 않습니다."),
    SECRECY("비밀 유지 요구", "가족이나 은행 직원에게 알리지 말라고 요구했습니다."),
    REMOTE_CONTROL("원격 제어 요구", "휴대전화 원격 제어 앱 설치나 화면 공유를 요구했습니다."),
    URGENCY("긴급 압박", "즉시 행동하지 않으면 불이익이 생긴다며 판단할 시간을 주지 않았습니다.");

    private final String label;
    private final String defaultExplanation;

    FraudPatternType(String label, String defaultExplanation) {
        this.label = label;
        this.defaultExplanation = defaultExplanation;
    }

    public String label() {
        return label;
    }

    public String defaultExplanation() {
        return defaultExplanation;
    }

    public static Optional<FraudPatternType> fromExternalValue(String value) {
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
