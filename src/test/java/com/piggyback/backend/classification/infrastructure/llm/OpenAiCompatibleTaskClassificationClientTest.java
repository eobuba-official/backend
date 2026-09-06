package com.piggyback.backend.classification.infrastructure.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import com.piggyback.backend.common.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleTaskClassificationClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        properties = new LlmProperties();
        properties.setBaseUrl("https://llm.example/v1");
        properties.setApiKey("test-token");
        properties.setPrimaryModel("primary-model");
        properties.setFallbackModel("primary-model");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
    }

    @Test
    void parsesStructuredOutputWithoutExposingUnknownFields() {
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var client = createClient();
        var output = client.analyze("통장을 잃어버렸어");

        assertEquals("primary-model", output.model());
        assertEquals(OpenAiCompatibleTaskClassificationClient.PROMPT_VERSION, output.promptVersion());
        assertEquals("통장을 잃어버렸어", output.correctedText());
        assertEquals("PASSBOOK_REISSUE", output.intent());
        assertEquals(0.93, output.confidence());
        assertFalse(output.fraudDetected());
        assertTrue(output.fraudPatterns().isEmpty());
        server.verify();
    }

    @Test
    void omitsTemperatureForGpt5Nano() {
        properties.setPrimaryModel("gpt-5-nano");
        properties.setFallbackModel("gpt-5-nano");
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("gpt-5-nano"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("gpt-5-nano", output.model());
        server.verify();
    }

    @Test
    void keepsTemperatureZeroForExistingModels() {
        properties.setPrimaryModel("gpt-4o-mini");
        properties.setFallbackModel("gpt-4o-mini");
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.temperature").value(0))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("gpt-4o-mini", output.model());
        server.verify();
    }

    @Test
    void usesResolvedModelFromProviderResponse() {
        properties.setPrimaryModel("gpt-5-nano");
        properties.setFallbackModel("gpt-5-nano");
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andRespond(withSuccess(
                        successResponse("gpt-5-nano-2025-08-07"),
                        MediaType.APPLICATION_JSON
                ));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("gpt-5-nano-2025-08-07", output.model());
        server.verify();
    }

    @Test
    void retriesOnceWithFallbackModel() {
        properties.setFallbackModel("fallback-model");
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("primary-model"))
                .andRespond(withServerError());
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("fallback-model"))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("fallback-model", output.model());
        server.verify();
    }

    @Test
    void mapsProviderBadRequestToLlmErrorWithoutReturningProviderDetails() {
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andRespond(withBadRequest().body("""
                        {
                          "error": {
                            "message": "Unsupported temperature containing sensitive utterance",
                            "type": "invalid_request_error",
                            "param": "temperature",
                            "code": "unsupported_value"
                          }
                        }
                        """).contentType(MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("민감한 사용자 발화")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("LLM request failed", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsMalformedStructuredOutput() {
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("통장을 잃어버렸어")
        );

        assertEquals("LLM returned malformed structured output", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsJsonThatOmitsRequiredStructuredFields() {
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"corrected_text\\\":\\\"잔액 알려줘\\\",\\\"intent\\\":\\\"BALANCE_INQUIRY\\\"}\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("LLM returned malformed structured output", exception.getMessage());
        server.verify();
    }

    @Test
    void mapsTimeoutToLlmError() {
        server.expect(once(), requestTo("https://llm.example/v1/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("read timed out");
                });

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("LLM request failed", exception.getMessage());
        server.verify();
    }

    @Test
    void failsFastWhenBaseUrlIsMissing() {
        properties.setBaseUrl(" ");

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("통장을 잃어버렸어")
        );

        assertEquals("LLM base URL is not configured", exception.getMessage());
    }

    private OpenAiCompatibleTaskClassificationClient createClient() {
        return new OpenAiCompatibleTaskClassificationClient(
                restClientBuilder.build(),
                new ObjectMapper(),
                properties
        );
    }

    private String successResponse() {
        return successResponse(null);
    }

    private String successResponse(String model) {
        String modelField = model == null ? "" : "\"model\":\"" + model + "\",";
        return """
                {
                  %s
                  "choices": [
                    {
                      "message": {
                        "content": "{\\\"corrected_text\\\":\\\"통장을 잃어버렸어\\\",\\\"fraud_detected\\\":false,\\\"fraud_patterns\\\":[],\\\"intent\\\":\\\"PASSBOOK_REISSUE\\\",\\\"confidence\\\":0.93,\\\"candidates\\\":[]}"
                      }
                    }
                  ]
                }
                """.formatted(modelField);
    }
}
