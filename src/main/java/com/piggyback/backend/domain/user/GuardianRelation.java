package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuardianRelation {

    SON("아들"),
    DAUGHTER("딸"),
    SPOUSE("배우자"),
    OTHER("기타");

    private final String label;

    public static GuardianRelation fromLabel(String label) {
        return Arrays.stream(values())
                .filter(relation -> relation.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "관계는 아들, 딸, 배우자, 기타 중 하나여야 합니다."));
    }
}
