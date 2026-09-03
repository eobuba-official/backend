package com.piggyback.backend.visit.service;

import static com.piggyback.backend.domain.TaskTypeCode.ACCOUNT_TRANSFER;
import static com.piggyback.backend.domain.TaskTypeCode.CARD_REISSUE;
import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static com.piggyback.backend.domain.VisitDecision.CHECK_NEEDED;
import static com.piggyback.backend.domain.VisitDecision.NO_VISIT;
import static com.piggyback.backend.domain.VisitDecision.VISIT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.entity.TaskVisitRule;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.repository.TaskTypeRepository;
import com.piggyback.backend.repository.TaskVisitRuleRepository;
import com.piggyback.backend.visit.domain.OfficialChannel;
import com.piggyback.backend.visit.domain.RemoteMethod;
import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VisitDecisionServiceTest {

    @Mock
    private TaskTypeRepository taskTypeRepository;

    @Mock
    private TaskVisitRuleRepository taskVisitRuleRepository;

    private VisitDecisionService visitDecisionService;

    @BeforeEach
    void setUp() {
        visitDecisionService = new VisitDecisionService(taskTypeRepository, taskVisitRuleRepository);
    }

    @Test
    void returnsVisitRequiredDecision() {
        TaskType taskType = taskType(PASSBOOK_REISSUE, VISIT_REQUIRED);
        TaskVisitRule rule = new TaskVisitRule(
                PASSBOOK_REISSUE,
                VISIT_REQUIRED,
                "본인 확인을 위해 지점 방문이 필요합니다.",
                null,
                null
        );
        when(taskTypeRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.of(taskType));
        when(taskVisitRuleRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.of(rule));

        VisitDecisionResponse response = visitDecisionService.decide(PASSBOOK_REISSUE);

        assertThat(response.decision()).isEqualTo(VISIT_REQUIRED);
        assertThat(response.remoteMethods()).isEmpty();
        assertThat(response.officialChannels()).isEmpty();
    }

    @Test
    void returnsOnlyRemoteMethodsForNoVisitDecision() {
        TaskType taskType = taskType(ACCOUNT_TRANSFER, NO_VISIT);
        RemoteMethod app = new RemoteMethod("MOBILE_APP", "KB스타뱅킹 이체", "휴대폰 앱으로 보내기");
        TaskVisitRule rule = new TaskVisitRule(
                ACCOUNT_TRANSFER,
                NO_VISIT,
                "앱이나 ATM으로 처리할 수 있습니다.",
                List.of(app),
                List.of(new OfficialChannel("고객센터", "1588-9999", "전화 확인"))
        );
        when(taskTypeRepository.findById(ACCOUNT_TRANSFER)).thenReturn(Optional.of(taskType));
        when(taskVisitRuleRepository.findById(ACCOUNT_TRANSFER)).thenReturn(Optional.of(rule));

        VisitDecisionResponse response = visitDecisionService.decide(ACCOUNT_TRANSFER);

        assertThat(response.remoteMethods()).containsExactly(app);
        assertThat(response.officialChannels()).isEmpty();
    }

    @Test
    void returnsOnlyOfficialChannelsForCheckNeededDecision() {
        TaskType taskType = taskType(CARD_REISSUE, CHECK_NEEDED);
        OfficialChannel callCenter = new OfficialChannel("KB국민카드 고객센터", "1588-1688", "재발급 방법 확인");
        TaskVisitRule rule = new TaskVisitRule(
                CARD_REISSUE,
                CHECK_NEEDED,
                "카드 종류에 따라 먼저 확인이 필요합니다.",
                List.of(new RemoteMethod("MOBILE_APP", "카드 재발급", "앱으로 재발급")),
                List.of(callCenter)
        );
        when(taskTypeRepository.findById(CARD_REISSUE)).thenReturn(Optional.of(taskType));
        when(taskVisitRuleRepository.findById(CARD_REISSUE)).thenReturn(Optional.of(rule));

        VisitDecisionResponse response = visitDecisionService.decide(CARD_REISSUE);

        assertThat(response.remoteMethods()).isEmpty();
        assertThat(response.officialChannels()).containsExactly(callCenter);
    }

    @Test
    void fallsBackToTaskTypeDefaultWhenRuleDoesNotExist() {
        TaskType taskType = taskType(CARD_REISSUE, CHECK_NEEDED);
        when(taskTypeRepository.findById(CARD_REISSUE)).thenReturn(Optional.of(taskType));
        when(taskVisitRuleRepository.findById(CARD_REISSUE)).thenReturn(Optional.empty());

        VisitDecisionResponse response = visitDecisionService.decide(CARD_REISSUE);

        assertThat(response.decision()).isEqualTo(CHECK_NEEDED);
        assertThat(response.reason()).isNotBlank();
        assertThat(response.remoteMethods()).isEmpty();
        assertThat(response.officialChannels()).isEmpty();
    }

    @Test
    void throwsWhenTaskTypeDoesNotExist() {
        when(taskTypeRepository.findById(PASSBOOK_REISSUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitDecisionService.decide(PASSBOOK_REISSUE))
                .isInstanceOf(TaskTypeNotFoundException.class)
                .hasMessage(ErrorCode.TASK_TYPE_NOT_FOUND.getMessage());
    }

    private TaskType taskType(TaskTypeCode code, VisitDecision defaultDecision) {
        return new TaskType(code, "업무명", "쉬운 설명", defaultDecision);
    }
}
