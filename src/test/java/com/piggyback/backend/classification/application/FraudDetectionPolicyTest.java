package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudDetectionPolicyTest {

    private final FraudDetectionPolicy policy = new FraudDetectionPolicy();

    @Test
    void acceptsAllFiveAllowedPatternsWhenEvidenceAppearsInTheOriginalUtterance() {
        String utterance = "검찰입니다. 안전계좌로 보내고 가족에게 말하지 말고 원격 앱을 지금 당장 설치하세요.";
        var patterns = policy.evaluate(
                utterance,
                output(
                        false,
                        List.of(
                                pattern("IMPERSONATION", "검찰입니다"),
                                pattern("SAFE_ACCOUNT", "안전계좌"),
                                pattern("SECRECY", "가족에게 말하지 말고"),
                                pattern("REMOTE_CONTROL", "원격 앱"),
                                pattern("URGENCY", "지금 당장")
                        )
                )
        );

        assertFalse(patterns.isEmpty());
        assertEquals(
                List.of(
                        FraudPatternType.IMPERSONATION,
                        FraudPatternType.SAFE_ACCOUNT,
                        FraudPatternType.SECRECY,
                        FraudPatternType.REMOTE_CONTROL,
                        FraudPatternType.URGENCY
                ),
                patterns.stream().map(pattern -> pattern.type()).toList()
        );
    }

    @Test
    void removesUnknownBlankUnsupportedAndDuplicateEvidenceWhileKeepingOrder() {
        String utterance = "안전계좌로 지금 보내세요";
        var patterns = policy.evaluate(
                utterance,
                output(
                        true,
                        List.of(
                                pattern("SAFE_ACCOUNT", " 안전계좌 "),
                                pattern("safe_account", "안전계좌"),
                                pattern("UNKNOWN", "지금"),
                                pattern("URGENCY", " "),
                                pattern("URGENCY", "발화에 없는 문장")
                        )
                )
        );

        assertEquals(1, patterns.size());
        assertEquals(FraudPatternType.SAFE_ACCOUNT, patterns.get(0).type());
        assertEquals("안전계좌", patterns.get(0).evidence());
    }

    @Test
    void usesValidPatternsAsTheSourceOfTruthWhenDetectedFlagIsFalse() {
        var patterns = policy.evaluate(
                "아무에게도 말하지 마",
                output(false, List.of(pattern("SECRECY", "말하지 마")))
        );

        assertFalse(patterns.isEmpty());
    }

    @Test
    void treatsDetectedFlagAsFalseWhenNoPatternHasValidEvidence() {
        var patterns = policy.evaluate(
                "잔액을 알려줘",
                output(true, List.of(pattern("URGENCY", "지금 당장")))
        );

        assertTrue(patterns.isEmpty());
    }

    @Test
    void suppliesSafeDefaultExplanationWhenLlmExplanationIsBlank() {
        var patterns = policy.evaluate(
                "원격 앱을 설치하세요",
                new LlmAnalysisOutput(
                        "model",
                        "prompt",
                        "원격 앱을 설치하세요",
                        true,
                        List.of(new LlmFraudPattern("REMOTE_CONTROL", "원격 앱", " ")),
                        "",
                        0.2,
                        List.of()
                )
        );

        assertEquals(
                FraudPatternType.REMOTE_CONTROL.defaultExplanation(),
                patterns.get(0).explanation()
        );
    }

    private LlmAnalysisOutput output(boolean detected, List<LlmFraudPattern> patterns) {
        return new LlmAnalysisOutput(
                "model",
                "prompt",
                "corrected",
                detected,
                patterns,
                "",
                0.2,
                List.of()
        );
    }

    private LlmFraudPattern pattern(String type, String evidence) {
        return new LlmFraudPattern(type, evidence, "탐지 설명");
    }
}
