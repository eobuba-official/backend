package com.piggyback.backend.checklist.service;

import com.piggyback.backend.checklist.domain.ChecklistConditionCode;
import com.piggyback.backend.checklist.domain.ChecklistItemStatus;
import com.piggyback.backend.checklist.dto.ChecklistAnswerRequest;
import com.piggyback.backend.checklist.dto.ChecklistAnswerResponse;
import com.piggyback.backend.checklist.dto.ChecklistQuestionResponse;
import com.piggyback.backend.checklist.dto.ChecklistQuestionsResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistItemResponse;
import com.piggyback.backend.checklist.dto.ResolvedChecklistResponse;
import com.piggyback.backend.checklist.entity.ConsultationChecklistAnswer;
import com.piggyback.backend.checklist.repository.ConsultationChecklistAnswerRepository;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.consultation.Consultation;
import com.piggyback.backend.domain.consultation.ConsultationRepository;
import com.piggyback.backend.entity.ChecklistItem;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultationChecklistService {

    private final ConsultationRepository consultationRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ConsultationChecklistAnswerRepository answerRepository;

    public ChecklistQuestionsResponse getQuestions(Long userId, UUID consultationId) {
        ConsultationContext context = getContext(userId, consultationId);
        Map<ChecklistConditionCode, Boolean> answers = answersByCode(consultationId);

        List<ChecklistQuestionResponse> questions = context.items().stream()
                .filter(item -> !item.isRequired() && item.getConditionCode() != null)
                .map(ChecklistItem::getConditionCode)
                .distinct()
                .map(code -> new ChecklistQuestionResponse(
                        code,
                        code.getQuestion(),
                        "BOOLEAN",
                        answers.containsKey(code),
                        answers.get(code)
                ))
                .toList();

        return new ChecklistQuestionsResponse(questions);
    }

    @Transactional
    public ChecklistAnswerResponse saveAnswers(
            Long userId,
            UUID consultationId,
            ChecklistAnswerRequest request
    ) {
        ConsultationContext context = getContext(userId, consultationId);
        Set<ChecklistConditionCode> relevantCodes = context.items().stream()
                .map(ChecklistItem::getConditionCode)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<ChecklistConditionCode> requestedCodes = new HashSet<>();

        for (ChecklistAnswerRequest.Answer answer : request.answers()) {
            if (!requestedCodes.add(answer.conditionCode())) {
                throw invalidInput("같은 조건에 대한 답변을 중복해서 보낼 수 없습니다.");
            }
            if (!relevantCodes.contains(answer.conditionCode())) {
                throw invalidInput("현재 업무에 필요하지 않은 조건입니다: " + answer.conditionCode());
            }

            answerRepository.findByConsultationIdAndConditionCode(
                            consultationId.toString(),
                            answer.conditionCode()
                    )
                    .ifPresentOrElse(
                            saved -> saved.update(answer.value()),
                            () -> answerRepository.save(new ConsultationChecklistAnswer(
                                    consultationId.toString(),
                                    answer.conditionCode(),
                                    answer.value()
                            ))
                    );
        }

        return new ChecklistAnswerResponse(request.answers().size());
    }

    public ResolvedChecklistResponse getResolvedChecklist(Long userId, UUID consultationId) {
        ConsultationContext context = getContext(userId, consultationId);
        Map<ChecklistConditionCode, Boolean> answers = answersByCode(consultationId);

        List<ResolvedChecklistItemResponse> items = context.items().stream()
                .map(item -> resolve(item, answers))
                .toList();
        boolean resolved = items.stream()
                .noneMatch(item -> item.status() == ChecklistItemStatus.UNRESOLVED);

        return new ResolvedChecklistResponse(
                context.taskType().getCode(),
                context.taskType().getName(),
                resolved,
                items
        );
    }

    private ConsultationContext getContext(Long userId, UUID consultationId) {
        Consultation consultation = consultationRepository.findById(consultationId.toString())
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND));
        if (consultation.getTaskTypeCode() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "확정된 업무가 없는 상담입니다.");
        }

        TaskTypeCode taskTypeCode;
        try {
            taskTypeCode = TaskTypeCode.valueOf(consultation.getTaskTypeCode());
        } catch (IllegalArgumentException e) {
            throw new TaskTypeNotFoundException();
        }
        TaskType taskType = taskTypeRepository.findById(taskTypeCode)
                .orElseThrow(TaskTypeNotFoundException::new);
        List<ChecklistItem> items = checklistItemRepository
                .findByTaskTypeCodeOrderByDisplayOrderAsc(taskTypeCode);
        return new ConsultationContext(taskType, items);
    }

    private Map<ChecklistConditionCode, Boolean> answersByCode(UUID consultationId) {
        Map<ChecklistConditionCode, Boolean> answers = new EnumMap<>(ChecklistConditionCode.class);
        answerRepository.findAllByConsultationId(consultationId.toString())
                .forEach(answer -> answers.put(answer.getConditionCode(), answer.isAnswerValue()));
        return answers;
    }

    private ResolvedChecklistItemResponse resolve(
            ChecklistItem item,
            Map<ChecklistConditionCode, Boolean> answers
    ) {
        if (item.isRequired()) {
            return response(item, ChecklistItemStatus.INCLUDED, "항상 필요한 준비물이에요.");
        }
        if (item.getConditionCode() == null || item.getExpectedAnswer() == null) {
            return response(item, ChecklistItemStatus.UNRESOLVED, item.getItemCondition());
        }
        Boolean answer = answers.get(item.getConditionCode());
        if (answer == null) {
            return response(item, ChecklistItemStatus.UNRESOLVED, item.getConditionCode().getQuestion());
        }
        if (answer.equals(item.getExpectedAnswer())) {
            return response(item, ChecklistItemStatus.INCLUDED, item.getItemCondition());
        }
        return response(item, ChecklistItemStatus.EXCLUDED, item.getItemCondition());
    }

    private ResolvedChecklistItemResponse response(
            ChecklistItem item,
            ChecklistItemStatus status,
            String reason
    ) {
        return new ResolvedChecklistItemResponse(
                item.getItemCode(),
                item.getName(),
                item.getEasyDescription(),
                item.isRequired(),
                status,
                reason,
                item.getDisplayOrder()
        );
    }

    private BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private record ConsultationContext(TaskType taskType, List<ChecklistItem> items) {
    }
}
