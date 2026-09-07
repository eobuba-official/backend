package com.piggyback.backend.checklist.service;

import static com.piggyback.backend.checklist.domain.ChecklistConditionCode.IS_PROXY;
import static com.piggyback.backend.checklist.domain.ChecklistConditionCode.USES_SEAL;
import static com.piggyback.backend.checklist.domain.ChecklistItemStatus.EXCLUDED;
import static com.piggyback.backend.checklist.domain.ChecklistItemStatus.INCLUDED;
import static com.piggyback.backend.checklist.domain.ChecklistItemStatus.UNRESOLVED;
import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static com.piggyback.backend.domain.VisitDecision.VISIT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.checklist.dto.ChecklistAnswerRequest;
import com.piggyback.backend.checklist.dto.ChecklistQuestionsResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistResponse;
import com.piggyback.backend.checklist.entity.ConsultationChecklistAnswer;
import com.piggyback.backend.checklist.repository.ConsultationChecklistAnswerRepository;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.consultation.Consultation;
import com.piggyback.backend.domain.consultation.ConsultationRepository;
import com.piggyback.backend.domain.consultation.ConsultationStatus;
import com.piggyback.backend.domain.consultation.InputMethod;
import com.piggyback.backend.entity.ChecklistItem;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultationChecklistServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID CONSULTATION_ID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");

    @Mock
    private ConsultationRepository consultationRepository;
    @Mock
    private TaskTypeRepository taskTypeRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private ConsultationChecklistAnswerRepository answerRepository;

    private ConsultationChecklistService service;

    @BeforeEach
    void setUp() {
        service = new ConsultationChecklistService(
                consultationRepository,
                taskTypeRepository,
                checklistItemRepository,
                answerRepository
        );
    }

    @Test
    void returnsDistinctQuestionsWithSavedAnswers() {
        givenContext(List.of(
                conditionalItem("POA", IS_PROXY, true, 1),
                conditionalItem("FAMILY_CERT", IS_PROXY, true, 2),
                conditionalItem("SEAL", USES_SEAL, true, 3)
        ));
        when(answerRepository.findAllByConsultationId(CONSULTATION_ID.toString()))
                .thenReturn(List.of(new ConsultationChecklistAnswer(
                        CONSULTATION_ID.toString(), IS_PROXY, true
                )));

        ChecklistQuestionsResponse response = service.getQuestions(USER_ID, CONSULTATION_ID);

        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions().get(0).conditionCode()).isEqualTo(IS_PROXY);
        assertThat(response.questions().get(0).answered()).isTrue();
        assertThat(response.questions().get(0).answer()).isTrue();
        assertThat(response.questions().get(1).conditionCode()).isEqualTo(USES_SEAL);
        assertThat(response.questions().get(1).answered()).isFalse();
    }

    @Test
    void resolvesRequiredMatchedUnmatchedAndUnansweredItems() {
        ChecklistItem required = new ChecklistItem(
                PASSBOOK_REISSUE, "ID_CARD", "신분증", "신분증", true, null, 1
        );
        givenContext(List.of(
                required,
                conditionalItem("POA", IS_PROXY, true, 2),
                conditionalItem("SEAL", USES_SEAL, true, 3)
        ));
        when(answerRepository.findAllByConsultationId(CONSULTATION_ID.toString()))
                .thenReturn(List.of(new ConsultationChecklistAnswer(
                        CONSULTATION_ID.toString(), IS_PROXY, false
                )));

        ResolvedChecklistResponse response = service.getResolvedChecklist(USER_ID, CONSULTATION_ID);

        assertThat(response.resolved()).isFalse();
        assertThat(response.items()).extracting("status")
                .containsExactly(INCLUDED, EXCLUDED, UNRESOLVED);
    }

    @Test
    void savesNewRelevantAnswer() {
        givenLockedContext(List.of(conditionalItem("POA", IS_PROXY, true, 1)));
        when(answerRepository.findByConsultationIdAndConditionCode(
                CONSULTATION_ID.toString(), IS_PROXY
        )).thenReturn(Optional.empty());

        var response = service.saveAnswers(
                USER_ID,
                CONSULTATION_ID,
                new ChecklistAnswerRequest(List.of(new ChecklistAnswerRequest.Answer(IS_PROXY, true)))
        );

        assertThat(response.savedCount()).isEqualTo(1);
        verify(answerRepository).save(any(ConsultationChecklistAnswer.class));
    }

    @Test
    void rejectsConditionThatIsNotUsedByCurrentTask() {
        givenLockedContext(List.of(conditionalItem("POA", IS_PROXY, true, 1)));

        assertThatThrownBy(() -> service.saveAnswers(
                USER_ID,
                CONSULTATION_ID,
                new ChecklistAnswerRequest(List.of(new ChecklistAnswerRequest.Answer(USES_SEAL, true)))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void hidesConsultationOwnedByAnotherUser() {
        Consultation consultation = consultation(2L, PASSBOOK_REISSUE.name());
        when(consultationRepository.findById(CONSULTATION_ID.toString()))
                .thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> service.getQuestions(USER_ID, CONSULTATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONSULTATION_NOT_FOUND);
    }

    private void givenContext(List<ChecklistItem> items) {
        givenTaskTypeAndItems(items);
        when(consultationRepository.findById(CONSULTATION_ID.toString()))
                .thenReturn(Optional.of(consultation(USER_ID, PASSBOOK_REISSUE.name())));
    }

    private void givenLockedContext(List<ChecklistItem> items) {
        givenTaskTypeAndItems(items);
        when(consultationRepository.findByIdAndUserIdForUpdate(
                CONSULTATION_ID.toString(), USER_ID
        )).thenReturn(Optional.of(consultation(USER_ID, PASSBOOK_REISSUE.name())));
    }

    private void givenTaskTypeAndItems(List<ChecklistItem> items) {
        TaskType taskType = new TaskType(
                PASSBOOK_REISSUE, "통장 재발급", "통장을 새로 만드는 일", VISIT_REQUIRED
        );
        when(taskTypeRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.of(taskType));
        when(checklistItemRepository.findByTaskTypeCodeOrderByDisplayOrderAsc(PASSBOOK_REISSUE))
                .thenReturn(items);
    }

    private Consultation consultation(Long userId, String taskTypeCode) {
        return Consultation.builder()
                .id(CONSULTATION_ID.toString())
                .userId(userId)
                .utterance("통장을 재발급하고 싶어요")
                .correctedUtterance("통장을 재발급하고 싶어요")
                .inputMethod(InputMethod.TEXT)
                .status(ConsultationStatus.TASK_CONFIRMED)
                .confidence(BigDecimal.ONE)
                .taskTypeCode(taskTypeCode)
                .build();
    }

    private ChecklistItem conditionalItem(
            String itemCode,
            com.piggyback.backend.checklist.domain.ChecklistConditionCode conditionCode,
            boolean expectedAnswer,
            int displayOrder
    ) {
        return new ChecklistItem(
                PASSBOOK_REISSUE,
                itemCode,
                itemCode,
                "쉬운 설명",
                false,
                "조건 설명",
                conditionCode,
                expectedAnswer,
                displayOrder
        );
    }
}
