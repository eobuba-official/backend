package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ConsultationStatus;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import com.piggyback.backend.domain.TaskTypeCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class JpaClassificationResultStoreIntegrationTest {

    @Autowired
    private JpaClassificationResultStore store;

    @Autowired
    private ConsultationJpaRepository consultationRepository;

    @Autowired
    private ConsultationCandidateJpaRepository candidateRepository;

    @Autowired
    private ConsultationResultJpaRepository resultRepository;

    @Autowired
    private FraudDetectionJpaRepository fraudDetectionRepository;

    @Test
    void persistsCandidateOrderAndConfirmsOnlyAStoredCandidate() {
        var command = new ClassificationCommand("돈 관련 업무야", InputMethod.VOICE, 0.81);
        var result = ClassificationResult.candidates(
                "돈 관련 업무야",
                0.48,
                List.of(
                        TaskTypeView.from(TaskTypeCode.DEPOSIT_EARLY_CLOSE),
                        TaskTypeView.from(TaskTypeCode.AUTO_TRANSFER_CHANGE),
                        TaskTypeView.from(TaskTypeCode.BALANCE_INQUIRY)
                ),
                true
        );

        var consultationId = store.save(7L, command, result);
        var candidates = candidateRepository.findAllByConsultationIdOrderByDisplayOrder(
                consultationId.toString()
        );

        assertEquals(List.of(1, 2, 3), candidates.stream()
                .map(ConsultationCandidateEntity::displayOrder)
                .toList());
        assertEquals(
                ClassificationResultStore.SelectionOutcome.CONFIRMED,
                store.confirmCandidate(7L, consultationId, TaskTypeCode.AUTO_TRANSFER_CHANGE)
        );

        var consultation = consultationRepository.findById(consultationId.toString()).orElseThrow();
        assertEquals(ConsultationStatus.TASK_CONFIRMED, consultation.status());
        assertEquals(TaskTypeCode.AUTO_TRANSFER_CHANGE, consultation.taskTypeCode());
    }

    @Test
    void rejectsCandidateSelectionByAnotherUser() {
        var command = new ClassificationCommand("송금하고 싶어", InputMethod.TEXT, null);
        var result = ClassificationResult.candidates(
                "송금하고 싶어",
                0.5,
                List.of(
                        TaskTypeView.from(TaskTypeCode.ACCOUNT_TRANSFER),
                        TaskTypeView.from(TaskTypeCode.BALANCE_INQUIRY)
                ),
                false
        );
        var consultationId = store.save(7L, command, result);

        assertEquals(
                ClassificationResultStore.SelectionOutcome.CONSULTATION_NOT_FOUND,
                store.confirmCandidate(99L, consultationId, TaskTypeCode.ACCOUNT_TRANSFER)
        );
    }

    @Test
    void storesFraudSuspendedClassificationSeparately() {
        var command = new ClassificationCommand("안전계좌로 돈을 보내래", InputMethod.VOICE, null);
        var pending = ClassificationResult.confirmed(
                "안전계좌로 돈을 보내래",
                0.88,
                TaskTypeCode.ACCOUNT_TRANSFER,
                true
        );

        var consultationId = store.saveSuspended(
                7L,
                command,
                pending,
                List.of(new ValidatedFraudPattern(
                        FraudPatternType.SAFE_ACCOUNT,
                        "안전계좌",
                        "안전계좌 송금을 요구했습니다."
                ))
        );

        var consultation = consultationRepository.findById(consultationId.toString()).orElseThrow();
        assertEquals(ConsultationStatus.FRAUD_WARNING, consultation.status());
        assertEquals(null, consultation.taskTypeCode());
        var storedResult = resultRepository.findByConsultationId(consultationId.toString()).orElseThrow();
        assertEquals(pending.status(), storedResult.classificationStatus());
        assertEquals(TaskTypeCode.ACCOUNT_TRANSFER, storedResult.taskTypeCode());
        assertEquals(0.88, storedResult.confidence());
        var detections = fraudDetectionRepository.findAllByConsultationIdOrderById(
                consultationId.toString()
        );
        assertEquals(1, detections.size());
        assertEquals(FraudPatternType.SAFE_ACCOUNT, detections.get(0).patternType());
        assertEquals("안전계좌", detections.get(0).evidence());
    }

    @Test
    void storesSuspendedCandidatesAndFraudDetectionsInTheirDisplayOrder() {
        var command = new ClassificationCommand(
                "검찰이라며 비밀로 하고 송금하래",
                InputMethod.TEXT,
                null
        );
        var pending = ClassificationResult.candidates(
                command.utterance(),
                0.55,
                List.of(
                        TaskTypeView.from(TaskTypeCode.ACCOUNT_TRANSFER),
                        TaskTypeView.from(TaskTypeCode.BALANCE_INQUIRY)
                ),
                false
        );

        var consultationId = store.saveSuspended(
                7L,
                command,
                pending,
                List.of(
                        new ValidatedFraudPattern(
                                FraudPatternType.IMPERSONATION,
                                "검찰이라며",
                                "기관을 사칭했습니다."
                        ),
                        new ValidatedFraudPattern(
                                FraudPatternType.SECRECY,
                                "비밀로 하고",
                                "비밀 유지를 요구했습니다."
                        )
                )
        );

        var candidates = candidateRepository.findAllByConsultationIdOrderByDisplayOrder(
                consultationId.toString()
        );
        assertEquals(
                List.of(TaskTypeCode.ACCOUNT_TRANSFER, TaskTypeCode.BALANCE_INQUIRY),
                candidates.stream().map(ConsultationCandidateEntity::taskTypeCode).toList()
        );
        assertEquals(
                List.of(1, 2),
                candidates.stream().map(ConsultationCandidateEntity::displayOrder).toList()
        );
        var storedResult = resultRepository.findByConsultationId(consultationId.toString()).orElseThrow();
        assertEquals(pending.status(), storedResult.classificationStatus());
        assertEquals(null, storedResult.taskTypeCode());
        assertEquals(0.55, storedResult.confidence());
        assertEquals(
                List.of(FraudPatternType.IMPERSONATION, FraudPatternType.SECRECY),
                fraudDetectionRepository.findAllByConsultationIdOrderById(consultationId.toString())
                        .stream()
                        .map(FraudDetectionEntity::patternType)
                        .toList()
        );
    }
}
