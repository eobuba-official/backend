package com.piggyback.backend.domain.tasktype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.repository.TaskTypeRepository;
import com.piggyback.backend.domain.tasktype.dto.TaskTypeListResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskTypeServiceTest {

    private TaskTypeRepository taskTypeRepository;
    private TaskTypeService taskTypeService;

    @BeforeEach
    void setUp() {
        taskTypeRepository = mock(TaskTypeRepository.class);
        taskTypeService = new TaskTypeService(taskTypeRepository);
    }

    @Test
    void 업무유형_목록을_코드_이름_쉬운설명과_함께_반환한다() {
        when(taskTypeRepository.findAll()).thenReturn(List.of(
                new TaskType(TaskTypeCode.PASSBOOK_REISSUE, "통장 재발급",
                        "통장을 잃어버렸을 때 새로 만드는 일", VisitDecision.VISIT_REQUIRED)));

        TaskTypeListResponse response = taskTypeService.getTaskTypes();

        assertThat(response.taskTypes()).hasSize(1);
        var item = response.taskTypes().get(0);
        assertThat(item.code()).isEqualTo("PASSBOOK_REISSUE");
        assertThat(item.name()).isEqualTo("통장 재발급");
        assertThat(item.easyDescription()).isEqualTo("통장을 잃어버렸을 때 새로 만드는 일");
    }

    @Test
    void 업무유형은_코드_정의_순서로_정렬된다() {
        TaskType transfer = new TaskType(TaskTypeCode.ACCOUNT_TRANSFER, "계좌이체",
                "다른 사람에게 돈을 보내는 일", VisitDecision.NO_VISIT);
        TaskType passbook = new TaskType(TaskTypeCode.PASSBOOK_REISSUE, "통장 재발급",
                "통장을 잃어버렸을 때 새로 만드는 일", VisitDecision.VISIT_REQUIRED);
        when(taskTypeRepository.findAll()).thenReturn(List.of(transfer, passbook));

        TaskTypeListResponse response = taskTypeService.getTaskTypes();

        assertThat(response.taskTypes())
                .extracting(TaskTypeListResponse.TaskTypeItem::code)
                .containsExactly("PASSBOOK_REISSUE", "ACCOUNT_TRANSFER");
    }

    @Test
    void 업무유형이_없으면_빈_목록을_반환한다() {
        when(taskTypeRepository.findAll()).thenReturn(List.of());

        assertThat(taskTypeService.getTaskTypes().taskTypes()).isEmpty();
    }
}
