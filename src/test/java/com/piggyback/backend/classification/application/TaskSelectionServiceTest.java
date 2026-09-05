package com.piggyback.backend.classification.application;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.port.ClassificationResultStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskSelectionServiceTest {

    private final RecordingStore store = new RecordingStore();
    private final TaskSelectionService service = new TaskSelectionService(store);

    @Test
    void confirmsOneOfTheStoredCandidatesWithoutCallingTheLlm() {
        UUID consultationId = UUID.randomUUID();

        var result = service.select(7L, consultationId, "deposit_early_close");

        assertEquals(consultationId, result.consultationId());
        assertEquals(TaskTypeCode.DEPOSIT_EARLY_CLOSE, result.task().taskTypeCode());
        assertEquals(7L, store.userId);
        assertEquals(TaskTypeCode.DEPOSIT_EARLY_CLOSE, store.selectedTask);
    }

    @Test
    void mapsStoreFailuresToPublicSelectionReasons() {
        store.outcome = ClassificationResultStore.SelectionOutcome.CONSULTATION_NOT_FOUND;
        assertReason(TaskSelectionException.Reason.CONSULTATION_NOT_FOUND);

        store.outcome = ClassificationResultStore.SelectionOutcome.INVALID_STATE;
        assertReason(TaskSelectionException.Reason.INVALID_STATE);

        store.outcome = ClassificationResultStore.SelectionOutcome.TASK_NOT_CANDIDATE;
        assertReason(TaskSelectionException.Reason.TASK_TYPE_NOT_FOUND);
    }

    @Test
    void rejectsUnknownTaskCodeBeforeAccessingTheStore() {
        var exception = assertThrows(
                TaskSelectionException.class,
                () -> service.select(7L, UUID.randomUUID(), "DEPOSIT_MATURITY")
        );

        assertEquals(TaskSelectionException.Reason.TASK_TYPE_NOT_FOUND, exception.reason());
        assertEquals(0, store.calls);
    }

    private void assertReason(TaskSelectionException.Reason reason) {
        var exception = assertThrows(
                TaskSelectionException.class,
                () -> service.select(7L, UUID.randomUUID(), "ACCOUNT_TRANSFER")
        );
        assertEquals(reason, exception.reason());
    }

    private static class RecordingStore implements ClassificationResultStore {
        private SelectionOutcome outcome = SelectionOutcome.CONFIRMED;
        private long userId;
        private TaskTypeCode selectedTask;
        private int calls;

        @Override
        public UUID save(
                long userId,
                ClassificationCommand command,
                com.piggyback.backend.classification.domain.ClassificationResult result
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID saveSuspended(
                long userId,
                ClassificationCommand command,
                com.piggyback.backend.classification.domain.ClassificationResult pendingResult
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SelectionOutcome confirmCandidate(
                long userId,
                UUID consultationId,
                TaskTypeCode selectedTask
        ) {
            calls++;
            this.userId = userId;
            this.selectedTask = selectedTask;
            return outcome;
        }
    }
}
