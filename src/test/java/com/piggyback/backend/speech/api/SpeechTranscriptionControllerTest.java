package com.piggyback.backend.speech.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.exception.GlobalExceptionHandler;
import com.piggyback.backend.speech.application.SpeechTranscriptionService;
import com.piggyback.backend.speech.domain.TranscriptionSource;
import com.piggyback.backend.speech.dto.SpeechTranscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpeechTranscriptionControllerTest {

    private SpeechTranscriptionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(SpeechTranscriptionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SpeechTranscriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsClovaTranscriptionForAuthenticatedUser() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "speech.wav",
                "audio/wav",
                new byte[]{1, 2, 3}
        );
        when(service.transcribe(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("임시 문장")))
                .thenReturn(new SpeechTranscriptionResponse(
                        "최종 문장",
                        TranscriptionSource.CLOVA_CSR,
                        "임시 문장",
                        null,
                        true
                ));

        mockMvc.perform(multipart("/api/v1/speech/transcriptions")
                        .file(audio)
                        .param("browserTranscript", "임시 문장")
                        .requestAttr(JwtAuthFilter.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transcript").value("최종 문장"))
                .andExpect(jsonPath("$.data.source").value("CLOVA_CSR"))
                .andExpect(jsonPath("$.data.browserTranscript").value("임시 문장"))
                .andExpect(jsonPath("$.data.sttConfidence").isEmpty())
                .andExpect(jsonPath("$.data.recheckNeeded").value(true));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio",
                "speech.wav",
                "audio/wav",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/speech/transcriptions").file(audio))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }
}
