package com.piggyback.backend.config;

import com.piggyback.backend.checklist.domain.ChecklistConditionCode;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.entity.ChecklistItem;
import com.piggyback.backend.entity.TaskType;
import com.piggyback.backend.entity.TaskVisitRule;
import com.piggyback.backend.repository.ChecklistItemRepository;
import com.piggyback.backend.repository.TaskTypeRepository;
import com.piggyback.backend.repository.TaskVisitRuleRepository;
import com.piggyback.backend.visit.domain.OfficialChannel;
import com.piggyback.backend.visit.domain.RemoteMethod;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalReferenceDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalReferenceDataInitializer.class);

    private final TaskTypeRepository taskTypeRepository;
    private final TaskVisitRuleRepository taskVisitRuleRepository;
    private final ChecklistItemRepository checklistItemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int taskTypeCount = initializeTaskTypes();
        int visitRuleCount = initializeVisitRules();
        int checklistItemCount = initializeChecklistItems();

        log.info(
                "Local reference data initialized: taskTypes={}, visitRules={}, checklistItems={}",
                taskTypeCount,
                visitRuleCount,
                checklistItemCount
        );
    }

    private int initializeTaskTypes() {
        List<TaskType> missing = Arrays.stream(TaskTypeCode.values())
                .filter(code -> !taskTypeRepository.existsById(code))
                .map(code -> new TaskType(
                        code,
                        code.displayName(),
                        code.easyDescription(),
                        defaultVisitDecision(code)
                ))
                .toList();
        if (!missing.isEmpty()) {
            taskTypeRepository.saveAll(missing);
        }
        return missing.size();
    }

    private int initializeVisitRules() {
        List<TaskVisitRule> missing = visitRules().stream()
                .filter(rule -> !taskVisitRuleRepository.existsById(rule.getTaskTypeCode()))
                .toList();
        if (!missing.isEmpty()) {
            taskVisitRuleRepository.saveAll(missing);
        }
        return missing.size();
    }

    private int initializeChecklistItems() {
        List<ChecklistItem> missing = checklistItems().stream()
                .filter(item -> !checklistItemRepository.existsByTaskTypeCodeAndItemCode(
                        item.getTaskTypeCode(),
                        item.getItemCode()
                ))
                .toList();
        if (!missing.isEmpty()) {
            checklistItemRepository.saveAll(missing);
        }
        return missing.size();
    }

    private VisitDecision defaultVisitDecision(TaskTypeCode code) {
        return switch (code) {
            case PASSBOOK_REISSUE, PROXY_TASK -> VisitDecision.VISIT_REQUIRED;
            case DEPOSIT_EARLY_CLOSE, CARD_REISSUE, PASSWORD_CHANGE -> VisitDecision.CHECK_NEEDED;
            case AUTO_TRANSFER_CHANGE, BALANCE_INQUIRY, ACCOUNT_TRANSFER -> VisitDecision.NO_VISIT;
        };
    }

    private List<TaskVisitRule> visitRules() {
        return List.of(
                visitRule(
                        TaskTypeCode.PASSBOOK_REISSUE,
                        VisitDecision.VISIT_REQUIRED,
                        "통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다."
                ),
                visitRule(
                        TaskTypeCode.PROXY_TASK,
                        VisitDecision.VISIT_REQUIRED,
                        "가족 일을 대신 처리하려면 서류 확인이 필요해 지점 방문이 필요합니다."
                ),
                new TaskVisitRule(
                        TaskTypeCode.DEPOSIT_EARLY_CLOSE,
                        VisitDecision.CHECK_NEEDED,
                        "상품에 따라 앱에서 해지가 가능할 수 있어요. 먼저 확인해 보세요.",
                        List.of(),
                        List.of(new OfficialChannel(
                                "KB국민은행 고객센터",
                                "1588-9999",
                                "해지 가능 여부를 전화로 확인"
                        ))
                ),
                new TaskVisitRule(
                        TaskTypeCode.CARD_REISSUE,
                        VisitDecision.CHECK_NEEDED,
                        "카드 종류에 따라 앱이나 전화로 재발급이 가능할 수 있어요. 먼저 확인해 보세요.",
                        List.of(),
                        List.of(new OfficialChannel(
                                "KB국민카드 고객센터",
                                "1588-1688",
                                "재발급 방법을 전화로 확인"
                        ))
                ),
                new TaskVisitRule(
                        TaskTypeCode.PASSWORD_CHANGE,
                        VisitDecision.CHECK_NEEDED,
                        "비밀번호 종류에 따라 앱에서 바꿀 수 있어요. 먼저 확인해 보세요.",
                        List.of(),
                        List.of(new OfficialChannel(
                                "KB국민은행 고객센터",
                                "1588-9999",
                                "변경 가능한 방법을 전화로 확인"
                        ))
                ),
                new TaskVisitRule(
                        TaskTypeCode.AUTO_TRANSFER_CHANGE,
                        VisitDecision.NO_VISIT,
                        "자동이체 변경은 앱이나 전화로 하실 수 있어요.",
                        List.of(
                                new RemoteMethod("MOBILE_APP", "KB스타뱅킹 자동이체 관리", "휴대폰 앱으로 바꾸기"),
                                new RemoteMethod("CALL_CENTER", "고객센터 1588-9999", "전화로 바꾸기")
                        ),
                        List.of()
                ),
                new TaskVisitRule(
                        TaskTypeCode.BALANCE_INQUIRY,
                        VisitDecision.NO_VISIT,
                        "잔액과 거래내역은 앱이나 ATM에서 보실 수 있어요.",
                        List.of(
                                new RemoteMethod("MOBILE_APP", "KB스타뱅킹 조회", "휴대폰 앱으로 보기"),
                                new RemoteMethod("ATM", "ATM 조회", "은행 기계로 보기")
                        ),
                        List.of()
                ),
                new TaskVisitRule(
                        TaskTypeCode.ACCOUNT_TRANSFER,
                        VisitDecision.NO_VISIT,
                        "계좌이체는 앱이나 ATM으로 하실 수 있어요.",
                        List.of(
                                new RemoteMethod("MOBILE_APP", "KB스타뱅킹 이체", "휴대폰 앱으로 보내기"),
                                new RemoteMethod("ATM", "ATM 계좌이체", "은행 기계로 보내기")
                        ),
                        List.of()
                )
        );
    }

    private TaskVisitRule visitRule(TaskTypeCode code, VisitDecision decision, String reason) {
        return new TaskVisitRule(code, decision, reason, List.of(), List.of());
    }

    private List<ChecklistItem> checklistItems() {
        return List.of(
                required(TaskTypeCode.PASSBOOK_REISSUE, "ID_CARD", "신분증", "주민등록증이나 운전면허증", 1),
                conditional(TaskTypeCode.PASSBOOK_REISSUE, "SEAL", "도장", "통장 만들 때 쓴 도장",
                        "도장으로 만든 통장인 경우", ChecklistConditionCode.USES_SEAL, 2),
                conditional(TaskTypeCode.PASSBOOK_REISSUE, "POA", "위임장", "다른 사람이 대신 갈 때 필요한 종이",
                        "다른 사람이 대신 방문하는 경우", ChecklistConditionCode.IS_PROXY, 3),
                conditional(TaskTypeCode.PASSBOOK_REISSUE, "FAMILY_CERT", "가족관계증명서", "가족임을 증명하는 종이",
                        "다른 사람이 대신 방문하는 경우", ChecklistConditionCode.IS_PROXY, 4),
                required(TaskTypeCode.PROXY_TASK, "ID_CARD", "방문자 신분증",
                        "지점에 가는 분의 주민등록증이나 운전면허증", 1),
                required(TaskTypeCode.PROXY_TASK, "OWNER_ID_CARD", "본인 신분증",
                        "업무 당사자의 신분증 (사본 가능 여부는 지점 확인)", 2),
                required(TaskTypeCode.PROXY_TASK, "POA", "위임장", "일을 맡긴다는 내용을 적은 종이", 3),
                required(TaskTypeCode.PROXY_TASK, "FAMILY_CERT", "가족관계증명서", "가족임을 증명하는 종이", 4),
                required(TaskTypeCode.DEPOSIT_EARLY_CLOSE, "ID_CARD", "신분증", "주민등록증이나 운전면허증", 1),
                conditional(TaskTypeCode.DEPOSIT_EARLY_CLOSE, "PASSBOOK", "통장", "해지할 예금 통장",
                        "예금 통장을 가지고 있는 경우", ChecklistConditionCode.HAS_PASSBOOK, 2),
                conditional(TaskTypeCode.DEPOSIT_EARLY_CLOSE, "SEAL", "도장", "예금 만들 때 쓴 도장",
                        "도장으로 만든 예금인 경우", ChecklistConditionCode.USES_SEAL, 3),
                required(TaskTypeCode.CARD_REISSUE, "ID_CARD", "신분증", "주민등록증이나 운전면허증", 1),
                required(TaskTypeCode.PASSWORD_CHANGE, "ID_CARD", "신분증", "주민등록증이나 운전면허증", 1),
                conditional(TaskTypeCode.PASSWORD_CHANGE, "PASSBOOK", "통장", "비밀번호를 바꿀 통장",
                        "통장 비밀번호를 바꾸는 경우", ChecklistConditionCode.IS_PASSBOOK_PASSWORD_CHANGE, 2)
        );
    }

    private ChecklistItem required(
            TaskTypeCode taskTypeCode,
            String itemCode,
            String name,
            String easyDescription,
            int displayOrder
    ) {
        return new ChecklistItem(
                taskTypeCode,
                itemCode,
                name,
                easyDescription,
                true,
                null,
                displayOrder
        );
    }

    private ChecklistItem conditional(
            TaskTypeCode taskTypeCode,
            String itemCode,
            String name,
            String easyDescription,
            String condition,
            ChecklistConditionCode conditionCode,
            int displayOrder
    ) {
        return new ChecklistItem(
                taskTypeCode,
                itemCode,
                name,
                easyDescription,
                false,
                condition,
                conditionCode,
                true,
                displayOrder
        );
    }
}
