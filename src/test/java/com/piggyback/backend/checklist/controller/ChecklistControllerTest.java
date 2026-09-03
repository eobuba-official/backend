package com.piggyback.backend.checklist.controller;

import static com.piggyback.backend.domain.TaskTypeCode.PASSBOOK_REISSUE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piggyback.backend.checklist.dto.ChecklistItemResponse;
import com.piggyback.backend.checklist.dto.ChecklistResponse;
import com.piggyback.backend.checklist.service.ChecklistService;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.exception.TaskTypeNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChecklistControllerTest {

    private ChecklistService checklistService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checklistService = org.mockito.Mockito.mock(ChecklistService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChecklistController(checklistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsChecklistWrappedInApiResponse() throws Exception {
        ChecklistItemResponse item = new ChecklistItemResponse(
                "ID_CARD",
                "신분증",
                "주민등록증이나 운전면허증",
                true,
                null,
                1
        );
        when(checklistService.getChecklist(PASSBOOK_REISSUE))
                .thenReturn(new ChecklistResponse(PASSBOOK_REISSUE, "통장 재발급", List.of(item)));

        mockMvc.perform(get("/api/v1/task-types/PASSBOOK_REISSUE/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.taskTypeCode").value("PASSBOOK_REISSUE"))
                .andExpect(jsonPath("$.data.taskTypeName").value("통장 재발급"))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("ID_CARD"))
                .andExpect(jsonPath("$.data.items[0].required").value(true))
                .andExpect(jsonPath("$.data.items[0].condition").isEmpty());
    }

    @Test
    void returnsTaskTypeNotFoundError() throws Exception {
        when(checklistService.getChecklist(PASSBOOK_REISSUE))
                .thenThrow(new TaskTypeNotFoundException());

        mockMvc.perform(get("/api/v1/task-types/PASSBOOK_REISSUE/checklist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("TASK_TYPE_NOT_FOUND"));
    }

    @Test
    void returnsInvalidInputForUnknownTaskTypeCode() throws Exception {
        mockMvc.perform(get("/api/v1/task-types/UNKNOWN/checklist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
