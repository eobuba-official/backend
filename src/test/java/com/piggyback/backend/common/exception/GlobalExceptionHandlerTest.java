package com.piggyback.backend.common.exception;

import com.piggyback.backend.common.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 성공_응답은_success_data_error_구조로_감싼다() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void BusinessException은_에러코드의_상태와_코드로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("CONSULTATION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("상담을 찾을 수 없습니다."));
    }

    @Test
    void 커스텀_메시지를_가진_BusinessException은_해당_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/test/invalid-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.error.message").value("후보 선택 상태가 아닙니다."));
    }

    @Test
    void LLM_ERROR는_502로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/llm-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("LLM_ERROR"));
    }

    @Test
    void 검증_실패는_INVALID_INPUT_400으로_매핑된다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 본문_파싱_실패는_INVALID_INPUT_400으로_매핑된다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void 미처리_예외는_INTERNAL_ERROR_500으로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Validated
    @RestController
    static class TestController {

        record TestRequest(@NotBlank(message = "name은 필수입니다.") String name) {
        }

        @GetMapping("/test/ok")
        ApiResponse<String> ok() {
            return ApiResponse.success("ok");
        }

        @GetMapping("/test/not-found")
        ApiResponse<Void> notFound() {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }

        @GetMapping("/test/invalid-state")
        ApiResponse<Void> invalidState() {
            throw new BusinessException(ErrorCode.INVALID_STATE, "후보 선택 상태가 아닙니다.");
        }

        @GetMapping("/test/llm-error")
        ApiResponse<Void> llmError() {
            throw new BusinessException(ErrorCode.LLM_ERROR);
        }

        @PostMapping("/test/validate")
        ApiResponse<Void> validate(@RequestBody @jakarta.validation.Valid TestRequest request) {
            return ApiResponse.success();
        }

        @GetMapping("/test/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("boom");
        }
    }
}
