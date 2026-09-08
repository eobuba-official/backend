package com.piggyback.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerUiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("어부바 백엔드 API"))
                .andExpect(jsonPath("$.info.version").value("v1.2"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/analyze']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/analyze'].post.summary").value("자연어 업무 분석"))
                .andExpect(jsonPath("$.paths['/api/v1/analyze'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/analyze'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/speech/transcriptions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/speech/transcriptions'].post.summary")
                        .value("음성 파일 인식"))
                .andExpect(jsonPath("$.paths['/api/v1/consultations/{consultationId}/task-selection']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/consultations/{consultationId}/task-selection']"
                                + ".post.summary")
                        .value("업무 후보 선택"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/sms/request'].post.tags[0]").value("인증"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/sms/verify'].post.responses['401'].description")
                        .value("인증번호 불일치, 만료 또는 인증 시도 횟수 초과"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.summary").value("내 정보 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/users/me/guardians'].post.responses['409'].description")
                        .value("보호자 최대 등록 수 3명 초과"))
                .andExpect(jsonPath("$.paths['/api/v1/task-types/{taskTypeCode}/checklist'].get.tags[0]")
                        .value("준비물"))
                .andExpect(jsonPath("$.paths['/api/v1/consultations/{consultationId}/checklist'].get.summary")
                        .value("최종 준비물 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/consultations/{consultationId}/checklist']"
                                + ".get.responses['409'].description")
                        .value("상담에 확정된 업무가 없음"))
                .andExpect(jsonPath("$.paths['/api/v1/branches/recommendations'].get.summary")
                        .value("방문 지점 및 시간 추천"))
                .andExpect(jsonPath("$.paths['/api/v1/branches/recommendations'].get.responses['200'].description")
                        .value("지점 추천 완료. 추천 가능한 지점이 없으면 빈 목록 반환"))
                .andExpect(jsonPath("$.paths['/api/v1/branches/recommendations'].get.parameters[?(@.name == 'userId')]")
                        .isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/task-types'].get.tags[0]").value("업무 유형"))
                .andExpect(jsonPath("$.paths['/api/health'].get.tags[0]").value("서버 상태"));
    }

    @Test
    void redirectsSwaggerEntryPointToTheUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
