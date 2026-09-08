package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ConsultationStatus;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.domain.TaskTypeView;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JpaClassificationResultStoreTest {

    private ConsultationJpaRepository consultationRepository;
    private ConsultationCandidateJpaRepository candidateRepository;
    private ConsultationResultJpaRepository resultRepository;
    private FraudDetectionJpaRepository fraudDetectionRepository;
    private JpaClassificationResultStore store;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationJpaRepository.class);
        candidateRepository = mock(ConsultationCandidateJpaRepository.class);
        resultRepository = mock(ConsultationResultJpaRepository.class);
        fraudDetectionRepository = mock(FraudDetectionJpaRepository.class);
        store = new JpaClassificationResultStore(
                consultationRepository,
                candidateRepository,
                resultRepository,
                fraudDetectionRepository,
                Clock.fixed(Instant.parse("2026-09-03T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void storesCandidatesInTheirDisplayOrder() {
        var command = new ClassificationCommand("돈 업무를 하고 싶어", InputMethod.VOICE, null);
        var result = ClassificationResult.candidates(
                "은행 돈 업무를 하고 싶어",
                0.48,
                List.of(
                        TaskTypeView.from(TaskTypeCode.DEPOSIT_EARLY_CLOSE),
                        TaskTypeView.from(TaskTypeCode.AUTO_TRANSFER_CHANGE)
                ),
                true
        );

        UUID consultationId = store.save(7L, command, result);

        var consultationCaptor = ArgumentCaptor.forClass(ConsultationEntity.class);
        verify(consultationRepository).save(consultationCaptor.capture());
        assertEquals(consultationId.toString(), consultationCaptor.getValue().id());
        assertEquals(7L, consultationCaptor.getValue().userId());
        assertEquals(ConsultationStatus.CANDIDATES_SUGGESTED, consultationCaptor.getValue().status());
        assertEquals("은행 돈 업무를 하고 싶어", consultationCaptor.getValue().correctedUtterance());

        var candidateCaptor = ArgumentCaptor.forClass(ConsultationCandidateEntity.class);
        verify(candidateRepository, org.mockito.Mockito.times(2)).save(candidateCaptor.capture());
        assertEquals(
                List.of(TaskTypeCode.DEPOSIT_EARLY_CLOSE, TaskTypeCode.AUTO_TRANSFER_CHANGE),
                candidateCaptor.getAllValues().stream()
                        .map(ConsultationCandidateEntity::taskTypeCode)
                        .toList()
        );
        assertEquals(
                List.of(1, 2),
                candidateCaptor.getAllValues().stream()
                        .map(ConsultationCandidateEntity::displayOrder)
                        .toList()
        );
    }

    @Test
    void rejectsSuspendedPersistenceWithoutValidatedFraudPatterns() {
        var command = new ClassificationCommand("잔액 알려줘", InputMethod.TEXT, null);
        var result = ClassificationResult.confirmed(
                "잔액 알려줘",
                0.9,
                TaskTypeCode.BALANCE_INQUIRY,
                false
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> store.saveSuspended(7L, command, result, List.of())
        );

        verifyNoInteractions(
                consultationRepository,
                candidateRepository,
                resultRepository,
                fraudDetectionRepository
        );
    }

    @Test
    void storesCorrectionConfirmationWithoutFinalizingTheTask() {
        var command = new ClassificationCommand("잘못 인식된 원문", InputMethod.VOICE, null);
        var result = ClassificationResult.confirmed(
                command.utterance(),
                "통장을 잃어버려서 다시 만들고 싶어",
                0.9,
                TaskTypeCode.PASSBOOK_REISSUE,
                true
        );

        store.saveAwaitingCorrectionConfirmation(7L, command, result);

        var captor = ArgumentCaptor.forClass(ConsultationEntity.class);
        verify(consultationRepository).save(captor.capture());
        assertEquals(ConsultationStatus.UNCLASSIFIED, captor.getValue().status());
        assertEquals(true, captor.getValue().awaitsCorrectionConfirmation());
        assertEquals(null, captor.getValue().taskTypeCode());
        verify(resultRepository).save(org.mockito.ArgumentMatchers.any(ConsultationResultEntity.class));
    }

    @Test
    void completesCorrectionConfirmationOnlyFromTheExpectedState() {
        UUID consultationId = UUID.randomUUID();
        var consultation = new ConsultationEntity(
                consultationId,
                7L,
                "잘못 인식된 원문",
                "통장을 다시 만들고 싶어",
                InputMethod.VOICE,
                null,
                ConsultationStatus.UNCLASSIFIED,
                0.4,
                null,
                java.time.LocalDateTime.now()
        );
        when(consultationRepository.findOwnedForUpdate(consultationId.toString(), 7L))
                .thenReturn(Optional.of(consultation));
        var result = ClassificationResult.confirmed(
                "잘못 인식된 원문",
                "통장을 잃어버려서 다시 만들고 싶어",
                0.93,
                TaskTypeCode.PASSBOOK_REISSUE,
                false
        );
        var pendingResult = new ConsultationResultEntity(
                consultationId.toString(),
                null,
                0.4,
                com.piggyback.backend.classification.domain.ClassificationStatus.UNCLASSIFIED
        );
        when(resultRepository.findByConsultationId(consultationId.toString()))
                .thenReturn(Optional.of(pendingResult));

        var outcome = store.completeCorrectionConfirmation(
                7L,
                consultationId,
                result.correctedUtterance(),
                result,
                List.of()
        );

        assertEquals(ClassificationResultStore.ConfirmationOutcome.COMPLETED, outcome);
        assertEquals(ConsultationStatus.TASK_CONFIRMED, consultation.status());
        assertEquals(TaskTypeCode.PASSBOOK_REISSUE, consultation.taskTypeCode());
        assertEquals(result.correctedUtterance(), consultation.correctedUtterance());
        verify(candidateRepository).deleteAllByConsultationId(consultationId.toString());
        verify(resultRepository).delete(pendingResult);
    }

    @Test
    void confirmsOnlyAnOwnedCandidateFromTheExpectedState() {
        UUID consultationId = UUID.randomUUID();
        var consultation = new ConsultationEntity(
                consultationId,
                7L,
                "송금하고 싶어",
                "송금하고 싶어",
                InputMethod.TEXT,
                null,
                ConsultationStatus.CANDIDATES_SUGGESTED,
                0.5,
                null,
                java.time.LocalDateTime.now()
        );
        when(consultationRepository.findOwnedForUpdate(consultationId.toString(), 7L))
                .thenReturn(Optional.of(consultation));
        when(candidateRepository.existsByConsultationIdAndTaskTypeCode(
                consultationId.toString(),
                TaskTypeCode.ACCOUNT_TRANSFER
        )).thenReturn(true);

        var outcome = store.confirmCandidate(7L, consultationId, TaskTypeCode.ACCOUNT_TRANSFER);

        assertEquals(ClassificationResultStore.SelectionOutcome.CONFIRMED, outcome);
        assertEquals(ConsultationStatus.TASK_CONFIRMED, consultation.status());
        assertEquals(TaskTypeCode.ACCOUNT_TRANSFER, consultation.taskTypeCode());
    }

    @Test
    void hidesAnotherUsersConsultationAsNotFound() {
        UUID consultationId = UUID.randomUUID();
        when(consultationRepository.findOwnedForUpdate(consultationId.toString(), 99L))
                .thenReturn(Optional.empty());

        var outcome = store.confirmCandidate(99L, consultationId, TaskTypeCode.ACCOUNT_TRANSFER);

        assertEquals(ClassificationResultStore.SelectionOutcome.CONSULTATION_NOT_FOUND, outcome);
        verify(candidateRepository, never()).existsByConsultationIdAndTaskTypeCode(
                consultationId.toString(),
                TaskTypeCode.ACCOUNT_TRANSFER
        );
    }
}
