package com.piggyback.backend.classification.infrastructure.persistence;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.ClassificationStatus;
import com.piggyback.backend.classification.domain.ConsultationStatus;
import com.piggyback.backend.classification.domain.ValidatedFraudPattern;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaClassificationResultStore implements ClassificationResultStore {

    private final ConsultationJpaRepository consultationRepository;
    private final ConsultationCandidateJpaRepository candidateRepository;
    private final ConsultationResultJpaRepository resultRepository;
    private final FraudDetectionJpaRepository fraudDetectionRepository;
    private final Clock clock;

    @Autowired
    public JpaClassificationResultStore(
            ConsultationJpaRepository consultationRepository,
            ConsultationCandidateJpaRepository candidateRepository,
            ConsultationResultJpaRepository resultRepository,
            FraudDetectionJpaRepository fraudDetectionRepository
    ) {
        this(
                consultationRepository,
                candidateRepository,
                resultRepository,
                fraudDetectionRepository,
                Clock.systemDefaultZone()
        );
    }

    JpaClassificationResultStore(
            ConsultationJpaRepository consultationRepository,
            ConsultationCandidateJpaRepository candidateRepository,
            ConsultationResultJpaRepository resultRepository,
            FraudDetectionJpaRepository fraudDetectionRepository,
            Clock clock
    ) {
        this.consultationRepository = consultationRepository;
        this.candidateRepository = candidateRepository;
        this.resultRepository = resultRepository;
        this.fraudDetectionRepository = fraudDetectionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID save(long userId, ClassificationCommand command, ClassificationResult result) {
        return saveConsultation(userId, command, result, toConsultationStatus(result.status()), false);
    }

    @Override
    @Transactional
    public UUID saveSuspended(
            long userId,
            ClassificationCommand command,
            ClassificationResult pendingResult,
            List<ValidatedFraudPattern> fraudPatterns
    ) {
        if (fraudPatterns == null || fraudPatterns.isEmpty()) {
            throw new IllegalArgumentException("Fraud warning requires at least one validated pattern");
        }
        UUID consultationId = saveConsultation(
                userId,
                command,
                pendingResult,
                ConsultationStatus.FRAUD_WARNING,
                true
        );
        for (ValidatedFraudPattern pattern : fraudPatterns) {
            fraudDetectionRepository.save(new FraudDetectionEntity(
                    consultationId.toString(),
                    pattern.type(),
                    pattern.evidence(),
                    pattern.explanation()
            ));
        }
        return consultationId;
    }

    private UUID saveConsultation(
            long userId,
            ClassificationCommand command,
            ClassificationResult result,
            ConsultationStatus consultationStatus,
            boolean suspended
    ) {
        UUID consultationId = UUID.randomUUID();
        TaskTypeCode classifiedTask = result.task() == null ? null : result.task().taskTypeCode();
        var entity = new ConsultationEntity(
                consultationId,
                userId,
                command.utterance(),
                result.correctedUtterance(),
                command.inputMethod(),
                command.sttConfidence(),
                consultationStatus,
                result.confidence(),
                suspended ? null : classifiedTask,
                LocalDateTime.now(clock)
        );
        consultationRepository.save(entity);

        if (suspended) {
            resultRepository.save(new ConsultationResultEntity(
                    consultationId.toString(),
                    classifiedTask,
                    result.confidence(),
                    result.status()
            ));
        }

        for (int index = 0; index < result.candidates().size(); index++) {
            candidateRepository.save(new ConsultationCandidateEntity(
                    consultationId.toString(),
                    result.candidates().get(index).taskTypeCode(),
                    index + 1
            ));
        }
        return consultationId;
    }

    @Override
    @Transactional
    public SelectionOutcome confirmCandidate(long userId, UUID consultationId, TaskTypeCode selectedTask) {
        var consultation = consultationRepository
                .findOwnedForUpdate(consultationId.toString(), userId)
                .orElse(null);
        if (consultation == null) {
            return SelectionOutcome.CONSULTATION_NOT_FOUND;
        }
        if (consultation.status() != ConsultationStatus.CANDIDATES_SUGGESTED) {
            return SelectionOutcome.INVALID_STATE;
        }
        if (!candidateRepository.existsByConsultationIdAndTaskTypeCode(
                consultationId.toString(),
                selectedTask
        )) {
            return SelectionOutcome.TASK_NOT_CANDIDATE;
        }

        consultation.confirm(selectedTask);
        return SelectionOutcome.CONFIRMED;
    }

    private ConsultationStatus toConsultationStatus(ClassificationStatus status) {
        return switch (status) {
            case CONFIRMED -> ConsultationStatus.TASK_CONFIRMED;
            case CANDIDATES -> ConsultationStatus.CANDIDATES_SUGGESTED;
            case UNCLASSIFIED -> ConsultationStatus.UNCLASSIFIED;
            case SUSPENDED -> throw new IllegalArgumentException(
                    "Suspended classification must be persisted by the fraud orchestration flow"
            );
        };
    }
}
