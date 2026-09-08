package com.piggyback.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.entity.ChecklistItem;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.entity.TaskVisitRule;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import com.piggyback.backend.repository.TaskVisitRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalReferenceDataInitializerTest {

    private final TaskTypeRepository taskTypeRepository = mock(TaskTypeRepository.class);
    private final TaskVisitRuleRepository visitRuleRepository = mock(TaskVisitRuleRepository.class);
    private final ChecklistItemRepository checklistItemRepository = mock(ChecklistItemRepository.class);
    private final LocalReferenceDataInitializer initializer = new LocalReferenceDataInitializer(
            taskTypeRepository,
            visitRuleRepository,
            checklistItemRepository
    );

    @Test
    void insertsAllMissingReferenceData() throws Exception {
        initializer.run(null);

        ArgumentCaptor<List<TaskType>> taskTypes = listCaptor();
        ArgumentCaptor<List<TaskVisitRule>> visitRules = listCaptor();
        ArgumentCaptor<List<ChecklistItem>> checklistItems = listCaptor();
        verify(taskTypeRepository).saveAll(taskTypes.capture());
        verify(visitRuleRepository).saveAll(visitRules.capture());
        verify(checklistItemRepository).saveAll(checklistItems.capture());

        assertThat(taskTypes.getValue())
                .extracting(TaskType::getCode)
                .containsExactlyInAnyOrder(TaskTypeCode.values());
        assertThat(visitRules.getValue())
                .extracting(TaskVisitRule::getTaskTypeCode)
                .containsExactlyInAnyOrder(TaskTypeCode.values());
        assertThat(checklistItems.getValue()).hasSize(14);
        assertThat(checklistItems.getValue())
                .extracting(item -> item.getTaskTypeCode() + ":" + item.getItemCode())
                .doesNotHaveDuplicates();
    }

    @Test
    void leavesExistingReferenceDataUntouched() throws Exception {
        when(taskTypeRepository.existsById(any())).thenReturn(true);
        when(visitRuleRepository.existsById(any())).thenReturn(true);
        when(checklistItemRepository.existsByTaskTypeCodeAndItemCode(any(), any())).thenReturn(true);

        initializer.run(null);

        verify(taskTypeRepository, never()).saveAll(any());
        verify(visitRuleRepository, never()).saveAll(any());
        verify(checklistItemRepository, never()).saveAll(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
