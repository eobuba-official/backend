package com.piggyback.backend.classification.infrastructure.llm;

import com.piggyback.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiTaskClassificationClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GeminiProperties properties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        properties = new GeminiProperties();
        properties.setBaseUrl("https://generativelanguage.example/v1beta");
        properties.setApiKey("test-gemini-key");
        properties.setModel("gemini-3.5-flash-lite");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        properties.setMaxRetries(0);
        properties.setRetryDelay(Duration.ZERO);
    }

    @Test
    void sendsGeminiStructuredOutputRequestAndParsesResponse() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.example/v1beta/models/gemini-3.5-flash-lite:generateContent"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("통장을 잃어버렸어"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.intent.enum.length()")
                        .value(9))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("gemini-3.5-flash-lite-20260721", output.model());
        assertEquals(GeminiTaskClassificationClient.PROMPT_VERSION, output.promptVersion());
        assertEquals("통장을 잃어버렸어", output.correctedText());
        assertEquals("PASSBOOK_REISSUE", output.intent());
        assertEquals(0.93, output.confidence());
        assertFalse(output.fraudDetected());
        assertTrue(output.fraudPatterns().isEmpty());
        server.verify();
    }

    @Test
    void sendsDefinitionsAndExamplesForAllAllowedFraudPatterns() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("IMPERSONATION")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("SAFE_ACCOUNT")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("SECRECY")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("REMOTE_CONTROL")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("URGENCY")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("안전계좌로 보내세요")))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("원격 앱을 설치하세요")))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        createClient().analyze("통장을 잃어버렸어");

        server.verify();
    }

    @Test
    void usesConfiguredModelWhenProviderOmitsModelVersion() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess(successResponseWithoutModel(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("통장을 잃어버렸어");

        assertEquals("gemini-3.5-flash-lite", output.model());
        server.verify();
    }

    @Test
    void mapsProviderBadRequestToLlmErrorWithoutReturningProviderMessage() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withBadRequest().body("""
                        {
                          "error": {
                            "code": 400,
                            "message": "Sensitive user utterance and API request details",
                            "status": "INVALID_ARGUMENT"
                          }
                        }
                        """).contentType(MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("민감한 사용자 발화")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("Gemini request failed", exception.getMessage());
        server.verify();
    }

    @Test
    void mapsProviderRateLimitToLlmErrorWithoutRetrying() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("""
                                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("Gemini request failed", exception.getMessage());
        server.verify();
    }

    @Test
    void retriesServerErrorOnce() {
        properties.setMaxRetries(1);
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var output = createClient().analyze("잔액 알려줘");

        assertEquals("PASSBOOK_REISSUE", output.intent());
        server.verify();
    }

    @Test
    void countsFailedAttemptBeforeRetrying() {
        properties.setMaxRetries(1);
        properties.setRequestsPerDay(1);
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals("Gemini requests-per-day limit reached", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsMalformedStructuredOutput() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess(responseWithText("not-json"), MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("통장을 잃어버렸어")
        );

        assertEquals("LLM returned malformed structured output", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsJsonThatOmitsRequiredStructuredFields() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess(
                        responseWithText("{\"corrected_text\":\"잔액 알려줘\",\"intent\":\"BALANCE_INQUIRY\"}"),
                        MediaType.APPLICATION_JSON
                ));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals("LLM returned malformed structured output", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsEmptyCandidateResponse() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals("Gemini response did not include a candidate", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsAbnormalFinishReason() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(withSuccess(
                        responseWithTextAndFinishReason("{}", "MAX_TOKENS"),
                        MediaType.APPLICATION_JSON
                ));

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals("Gemini response did not finish normally", exception.getMessage());
        server.verify();
    }

    @Test
    void mapsTimeoutToLlmError() {
        server.expect(once(), requestTo(properties.generateContentUrl()))
                .andRespond(request -> {
                    throw new ResourceAccessException("read timed out");
                });

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("잔액 알려줘")
        );

        assertEquals(ErrorCode.LLM_ERROR, exception.getErrorCode());
        assertEquals("Gemini request failed", exception.getMessage());
        server.verify();
    }

    @Test
    void failsFastWhenApiKeyIsMissing() {
        properties.setApiKey(" ");

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("통장을 잃어버렸어")
        );

        assertEquals("Gemini API key is not configured", exception.getMessage());
    }

    @Test
    void failsFastWhenBaseUrlIsMissing() {
        properties.setBaseUrl(" ");

        var exception = assertThrows(
                LlmClassificationException.class,
                () -> createClient().analyze("통장을 잃어버렸어")
        );

        assertEquals("Gemini base URL is not configured", exception.getMessage());
    }

    private GeminiTaskClassificationClient createClient() {
        var quota = new GeminiRequestQuota(properties, Clock.systemUTC());
        return new GeminiTaskClassificationClient(
                restClientBuilder.build(),
                new ObjectMapper(),
                properties,
                quota
        );
    }

    private String successResponse() {
        return """
                {
                  "modelVersion": "gemini-3.5-flash-lite-20260721",
                  "candidates": [{
                    "content": {"parts": [{"text": "{\\"corrected_text\\":\\"통장을 잃어버렸어\\",\\"fraud_detected\\":false,\\"fraud_patterns\\":[],\\"intent\\":\\"PASSBOOK_REISSUE\\",\\"confidence\\":0.93,\\"candidates\\":[]}"}]},
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 100,
                    "candidatesTokenCount": 20,
                    "totalTokenCount": 120
                  }
                }
                """;
    }

    private String successResponseWithoutModel() {
        return responseWithText("{\"corrected_text\":\"통장을 잃어버렸어\",\"fraud_detected\":false,\"fraud_patterns\":[],\"intent\":\"PASSBOOK_REISSUE\",\"confidence\":0.93,\"candidates\":[]}");
    }

    private String responseWithText(String text) {
        return responseWithTextAndFinishReason(text, "STOP");
    }

    private String responseWithTextAndFinishReason(String text, String finishReason) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "%s"}]},
                    "finishReason": "%s"
                  }]
                }
                """.formatted(escaped, finishReason);
    }
}
