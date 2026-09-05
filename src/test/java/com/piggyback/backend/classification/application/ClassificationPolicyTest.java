package com.piggyback.backend.classification.application;

import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ClassificationSignal;
import com.piggyback.backend.classification.domain.ClassificationStatus;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.domain.TaskTypeCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationPolicyTest {

    private ClassificationPolicy policy;

    @BeforeEach
    void setUp() {
        ClassificationProperties properties = new ClassificationProperties();
        properties.setConfidenceThreshold(0.75);
        properties.setCandidateFloor(0.35);
        properties.setCandidateLimit(3);
        policy = new ClassificationPolicy(properties);
    }

    @Test
    void confirmsValidIntentAtConfidenceThreshold() {
        var command = new ClassificationCommand("통장 잃어버렸어", InputMethod.TEXT, null);
        var signal = new ClassificationSignal(
                "통장을 잃어버렸어",
                "PASSBOOK_REISSUE",
                0.75,
                List.of()
        );

        var result = policy.normalize(command, signal);

        assertEquals(ClassificationStatus.CONFIRMED, result.status());
        assertEquals(TaskTypeCode.PASSBOOK_REISSUE, result.task().taskTypeCode());
        assertTrue(result.candidates().isEmpty());
        assertFalse(result.sttRecheckNeeded());
        assertNull(result.guidance());
    }

    @Test
    void returnsTwoOrThreeUniqueValidCandidatesAtCandidateFloor() {
        var command = new ClassificationCommand("예금 그거 해줘", InputMethod.VOICE, null);
        var signal = new ClassificationSignal(
                "예금을 해지하고 싶어",
                "DEPOSIT_EARLY_CLOSE",
                0.35,
                List.of(
                        "DEPOSIT_EARLY_CLOSE",
                        "DEPOSIT_MATURITY",
                        "AUTO_TRANSFER_CHANGE",
                        "BALANCE_INQUIRY",
                        "ACCOUNT_TRANSFER"
                )
        );

        var result = policy.normalize(command, signal);

        assertEquals(ClassificationStatus.CANDIDATES, result.status());
        assertEquals(
                List.of(
                        TaskTypeCode.DEPOSIT_EARLY_CLOSE,
                        TaskTypeCode.AUTO_TRANSFER_CHANGE,
                        TaskTypeCode.BALANCE_INQUIRY
                ),
                result.candidates().stream().map(candidate -> candidate.taskTypeCode()).toList()
        );
        assertTrue(result.sttRecheckNeeded());
    }

    @Test
    void returnsUnclassifiedBelowCandidateFloor() {
        var command = new ClassificationCommand("그거 해줘", InputMethod.TEXT, null);
        var signal = new ClassificationSignal(
                "그거 해줘",
                "BALANCE_INQUIRY",
                0.349,
                List.of("BALANCE_INQUIRY", "ACCOUNT_TRANSFER")
        );

        var result = policy.normalize(command, signal);

        assertEquals(ClassificationStatus.UNCLASSIFIED, result.status());
        assertEquals(ClassificationResult.UNCLASSIFIED_GUIDANCE, result.guidance());
        assertNull(result.task());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void returnsUnclassifiedWhenFewerThanTwoValidCandidatesRemain() {
        var command = new ClassificationCommand("예금 일이야", InputMethod.TEXT, null);
        var signal = new ClassificationSignal(
                "예금 일이야",
                "UNKNOWN_TASK",
                0.5,
                List.of("UNKNOWN_TASK", "ACCOUNT_TRANSFER", "ACCOUNT_TRANSFER")
        );

        var result = policy.normalize(command, signal);

        assertEquals(ClassificationStatus.UNCLASSIFIED, result.status());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void doesNotExposeInvalidHighConfidenceIntent() {
        var command = new ClassificationCommand("알 수 없는 업무", InputMethod.TEXT, null);
        var signal = new ClassificationSignal(
                "알 수 없는 업무",
                "TRANSFER_ALL_MONEY",
                0.99,
                List.of("ACCOUNT_TRANSFER", "BALANCE_INQUIRY")
        );

        var result = policy.normalize(command, signal);

        assertEquals(ClassificationStatus.UNCLASSIFIED, result.status());
        assertNull(result.task());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void fallsBackToOriginalUtteranceWhenCorrectionIsBlank() {
        var command = new ClassificationCommand("자동이체 바꿔줘", InputMethod.TEXT, null);
        var signal = new ClassificationSignal(
                " ",
                "AUTO_TRANSFER_CHANGE",
                0.9,
                List.of()
        );

        var result = policy.normalize(command, signal);

        assertEquals("자동이체 바꿔줘", result.correctedUtterance());
    }

    @Test
    void rejectsInvalidCommandValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClassificationCommand(" ", InputMethod.TEXT, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClassificationCommand("잔액 조회", InputMethod.VOICE, 1.1)
        );
    }

    @Test
    void rejectsOverlappingThresholds() {
        ClassificationProperties properties = new ClassificationProperties();
        properties.setCandidateFloor(0.75);
        properties.setConfidenceThreshold(0.75);

        assertThrows(IllegalStateException.class, () -> new ClassificationPolicy(properties));
    }
}
