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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaClassificationResultStoreTest {

    private ConsultationJpaRepository consultationRepository;
    private ConsultationCandidateJpaRepository candidateRepository;
    private ConsultationResultJpaRepository resultRepository;
    private JpaClassificationResultStore store;

    @BeforeEach
    void setUp() {
        consultationRepository = mock(ConsultationJpaRepository.class);
        candidateRepository = mock(ConsultationCandidateJpaRepository.class);
        resultRepository = mock(ConsultationResultJpaRepository.class);
        store = new JpaClassificationResultStore(
                consultationRepository,
                candidateRepository,
                resultRepository,
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
