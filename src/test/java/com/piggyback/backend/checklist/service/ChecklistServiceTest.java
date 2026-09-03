package com.piggyback.backend.checklist.service;

import static com.piggyback.backend.domain.TaskTypeCode.ACCOUNT_TRANSFER;
import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static com.piggyback.backend.domain.VisitDecision.NO_VISIT;
import static com.piggyback.backend.domain.VisitDecision.VISIT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.piggyback.backend.checklist.dto.ChecklistResponse;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.entity.ChecklistItem;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock
    private TaskTypeRepository taskTypeRepository;

    @Mock
    private ChecklistItemRepository checklistItemRepository;

    private ChecklistService checklistService;

    @BeforeEach
    void setUp() {
        checklistService = new ChecklistService(taskTypeRepository, checklistItemRepository);
    }

    @Test
    void returnsRequiredAndConditionalItemsInDisplayOrder() {
        TaskType taskType = new TaskType(PASSBOOK_REISSUE, "통장 재발급", "통장을 새로 만드는 일", VISIT_REQUIRED);
        ChecklistItem requiredItem = checklistItem("ID_CARD", true, null, 1);
        ChecklistItem conditionalItem = checklistItem("SEAL", false, "서명으로 만든 통장이면 필요 없어요", 2);
        when(taskTypeRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.of(taskType));
        when(checklistItemRepository.findByTaskTypeCodeOrderByDisplayOrderAsc(PASSBOOK_REISSUE))
                .thenReturn(List.of(requiredItem, conditionalItem));

        ChecklistResponse response = checklistService.getChecklist(PASSBOOK_REISSUE);

        assertThat(response.taskTypeCode()).isEqualTo(PASSBOOK_REISSUE);
        assertThat(response.taskTypeName()).isEqualTo("통장 재발급");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).required()).isTrue();
        assertThat(response.items().get(0).condition()).isNull();
        assertThat(response.items().get(1).required()).isFalse();
        assertThat(response.items().get(1).condition()).isEqualTo("서명으로 만든 통장이면 필요 없어요");
        assertThat(response.items()).extracting("displayOrder").containsExactly(1, 2);
    }

    @Test
    void returnsEmptyItemsForTaskTypeWithoutChecklist() {
        TaskType taskType = new TaskType(ACCOUNT_TRANSFER, "계좌이체", "다른 계좌로 돈을 보내는 일", NO_VISIT);
        when(taskTypeRepository.findById(ACCOUNT_TRANSFER)).thenReturn(Optional.of(taskType));
        when(checklistItemRepository.findByTaskTypeCodeOrderByDisplayOrderAsc(ACCOUNT_TRANSFER))
                .thenReturn(List.of());

        ChecklistResponse response = checklistService.getChecklist(ACCOUNT_TRANSFER);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void throwsWhenTaskTypeDoesNotExist() {
        when(taskTypeRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checklistService.getChecklist(PASSBOOK_REISSUE))
                .isInstanceOf(TaskTypeNotFoundException.class)
                .hasMessage(ErrorCode.TASK_TYPE_NOT_FOUND.getMessage());
    }

    private ChecklistItem checklistItem(
            String itemCode,
            boolean required,
            String condition,
            int displayOrder
    ) {
        return new ChecklistItem(
                PASSBOOK_REISSUE,
                itemCode,
                itemCode,
                "쉬운 설명",
                required,
                condition,
                displayOrder
        );
    }
}
