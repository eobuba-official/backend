package com.piggyback.backend.visit.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitDecisionService {

    private static final String DEFAULT_REASON = "등록된 상세 규칙이 없어 기본 방문 안내를 제공합니다.";

    private final TaskTypeRepository taskTypeRepository;
    private final TaskVisitRuleRepository taskVisitRuleRepository;

    public VisitDecisionResponse decide(TaskTypeCode taskTypeCode) {
        TaskType taskType = taskTypeRepository.findById(taskTypeCode)
                .orElseThrow(TaskTypeNotFoundException::new);

        return taskVisitRuleRepository.findById(taskTypeCode)
                .map(rule -> createResponse(taskType, rule))
                .orElseGet(() -> createFallbackResponse(taskType));
    }

    private VisitDecisionResponse createResponse(TaskType taskType, TaskVisitRule rule) {
        List<RemoteMethod> remoteMethods = rule.getDecision() == VisitDecision.NO_VISIT
                ? rule.getRemoteMethods()
                : List.of();
        List<OfficialChannel> officialChannels = rule.getDecision() == VisitDecision.CHECK_NEEDED
                ? rule.getOfficialChannels()
                : List.of();

        return new VisitDecisionResponse(
                taskType.getCode(),
                taskType.getName(),
                rule.getDecision(),
                rule.getReason(),
                remoteMethods,
                officialChannels
        );
    }

    private VisitDecisionResponse createFallbackResponse(TaskType taskType) {
        return new VisitDecisionResponse(
                taskType.getCode(),
                taskType.getName(),
                taskType.getDefaultVisitDecision(),
                DEFAULT_REASON,
                List.of(),
                List.of()
        );
    }
}
