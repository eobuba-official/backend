package com.piggyback.backend.checklist.service;

import static com.piggyback.backend.checklist.domain.ChecklistConditionCode.IS_PROXY;
import static org.assertj.core.api.Assertions.assertThat;

import com.piggyback.backend.checklist.dto.ChecklistAnswerRequest;
import com.piggyback.backend.checklist.repository.ConsultationChecklistAnswerRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ConsultationChecklistConcurrencyTest {

    private static final Long USER_ID = 27001L;
    private static final UUID CONSULTATION_ID = UUID.fromString("27000000-0000-0000-0000-000000000001");

    @Autowired
    private ConsultationChecklistService service;

    @Autowired
    private ConsultationChecklistAnswerRepository answerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into task_type (code, name, easy_description, default_visit_decision)
                values ('PASSBOOK_REISSUE', '통장 재발급', '통장을 새로 만드는 일', 'VISIT_REQUIRED')
                """);
        jdbcTemplate.update("""
                insert into consultation
                    (id, user_id, utterance, corrected_utterance, input_method, status,
                     confidence, task_type_code, created_at)
                values (?, ?, '통장 재발급', '통장 재발급', 'TEXT', 'TASK_CONFIRMED',
                        1.0, 'PASSBOOK_REISSUE', current_timestamp)
                """, CONSULTATION_ID.toString(), USER_ID);
        jdbcTemplate.update("""
                insert into checklist_item
                    (task_type_code, item_code, name, easy_description, required,
                     item_condition, condition_code, expected_answer, display_order)
                values ('PASSBOOK_REISSUE', 'POA', '위임장', '대리 방문 시 필요한 종이', false,
                        '다른 사람이 대신 방문하는 경우', 'IS_PROXY', true, 1)
                """);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from consultation_checklist_answer where consultation_id = ?",
                CONSULTATION_ID.toString());
        jdbcTemplate.update("delete from checklist_item where task_type_code = 'PASSBOOK_REISSUE'");
        jdbcTemplate.update("delete from consultation where id = ?", CONSULTATION_ID.toString());
        jdbcTemplate.update("delete from task_type where code = 'PASSBOOK_REISSUE'");
    }

    @Test
    void serializesConcurrentAnswersForSameConsultation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = submitAnswer(executor, ready, start, true);
            Future<?> second = submitAnswer(executor, ready, start, false);

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(answerRepository.findAllByConsultationId(CONSULTATION_ID.toString()))
                    .singleElement()
                    .satisfies(answer -> assertThat(answer.getConditionCode()).isEqualTo(IS_PROXY));
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<?> submitAnswer(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            boolean value
    ) {
        return executor.submit(() -> {
            ready.countDown();
            start.await();
            service.saveAnswers(
                    USER_ID,
                    CONSULTATION_ID,
                    new ChecklistAnswerRequest(List.of(
                            new ChecklistAnswerRequest.Answer(IS_PROXY, value)
                    ))
            );
            return null;
        });
    }
}
