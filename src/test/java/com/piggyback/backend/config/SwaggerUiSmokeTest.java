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
                .andExpect(jsonPath("$.paths['/api/v1/analyze']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/speech/transcriptions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/consultations/{consultationId}/task-selection']")
                        .exists());
    }

    @Test
    void redirectsSwaggerEntryPointToTheUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
