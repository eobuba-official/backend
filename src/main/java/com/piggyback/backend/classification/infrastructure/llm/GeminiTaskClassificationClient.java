package com.piggyback.backend.classification.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import com.piggyback.backend.domain.TaskTypeCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class GeminiTaskClassificationClient implements TaskClassificationClient {

    static final String PROMPT_VERSION = "task-classification-v1.4-gemini-fraud-guardrail";
    private static final String API_KEY_HEADER = "x-goog-api-key";

    private static final Logger log = LoggerFactory.getLogger(GeminiTaskClassificationClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;
    private final GeminiRequestQuota requestQuota;

    @Autowired
    public GeminiTaskClassificationClient(
            ObjectMapper objectMapper,
            GeminiProperties properties,
            GeminiRequestQuota requestQuota
    ) {
        this(buildRestClient(RestClient.builder(), properties), objectMapper, properties, requestQuota);
    }

    public GeminiTaskClassificationClient(
            ObjectMapper objectMapper,
            GeminiProperties properties
    ) {
        this(objectMapper, properties, new GeminiRequestQuota(properties));
    }

    private static RestClient buildRestClient(RestClient.Builder builder, GeminiProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.requestFactory(requestFactory).build();
    }

    GeminiTaskClassificationClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            GeminiProperties properties,
            GeminiRequestQuota requestQuota
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.requestQuota = requestQuota;
    }

    @Override
    public LlmAnalysisOutput analyze(String utterance) {
        properties.validate();
        int retryCount = 0;
        while (true) {
            GeminiRequestQuota.QuotaUsage quotaUsage = requestQuota.acquire();
            Instant startedAt = Instant.now();
            try {
                GenerateContentResponse response = request(utterance);
                Candidate candidate = extractCandidate(response);
                String content = extractContent(candidate);
                LlmPayload payload = objectMapper.readValue(content, LlmPayload.class);
                validatePayload(payload);
                String responseModel = resolveResponseModel(response);
                UsageMetadata usage = response == null ? null : response.usageMetadata();
                logSuccess(responseModel, usage, startedAt, quotaUsage);
                return payload.toOutput(responseModel);
            } catch (JacksonException exception) {
                throw new LlmClassificationException("LLM returned malformed structured output", exception);
            } catch (RestClientResponseException exception) {
                logHttpFailure(exception, startedAt, quotaUsage);
                if (!isRetryable(exception) || retryCount >= properties.getMaxRetries()) {
                    throw new LlmClassificationException("Gemini request failed", exception);
                }
            } catch (RestClientException exception) {
                logTransportFailure(exception, startedAt, quotaUsage);
                if (retryCount >= properties.getMaxRetries()) {
                    throw new LlmClassificationException("Gemini request failed", exception);
                }
            }
            retryCount++;
            waitBeforeRetry(retryCount);
        }
    }

    private GenerateContentResponse request(String utterance) {
        return restClient.post()
                .uri(properties.generateContentUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, properties.getApiKey())
                .body(buildRequest(utterance))
                .retrieve()
                .body(GenerateContentResponse.class);
    }

    private void logSuccess(
            String responseModel,
            UsageMetadata usage,
            Instant startedAt,
            GeminiRequestQuota.QuotaUsage quotaUsage
    ) {
        log.info(
                "Gemini classification response received: requestedModel={}, responseModel={}, promptVersion={}, latencyMs={}, promptTokens={}, candidateTokens={}, totalTokens={}, minuteRemaining={}, dailyRemaining={}",
                properties.getModel(),
                responseModel,
                PROMPT_VERSION,
                Duration.between(startedAt, Instant.now()).toMillis(),
                tokenCount(usage == null ? null : usage.promptTokenCount()),
                tokenCount(usage == null ? null : usage.candidatesTokenCount()),
                tokenCount(usage == null ? null : usage.totalTokenCount()),
                quotaUsage.minuteRemaining(),
                quotaUsage.dailyRemaining()
        );
    }

    private void logTransportFailure(
            RestClientException exception,
            Instant startedAt,
            GeminiRequestQuota.QuotaUsage quotaUsage
    ) {
        log.warn(
                "Gemini transport request failed: requestedModel={}, promptVersion={}, latencyMs={}, cause={}, minuteRemaining={}, dailyRemaining={}",
                properties.getModel(),
                PROMPT_VERSION,
                Duration.between(startedAt, Instant.now()).toMillis(),
                exception.getClass().getSimpleName(),
                quotaUsage.minuteRemaining(),
                quotaUsage.dailyRemaining()
        );
    }

    private boolean isRetryable(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 408 || status >= 500;
    }

    private void waitBeforeRetry(int retryCount) {
        log.info(
                "Retrying Gemini classification request: requestedModel={}, promptVersion={}, retryCount={}",
                properties.getModel(),
                PROMPT_VERSION,
                retryCount
        );
        try {
            Thread.sleep(properties.getRetryDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmClassificationException("Gemini retry was interrupted", exception);
        }
    }

    private Map<String, Object> buildRequest(String utterance) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt()))
        ));
        request.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", utterance))
        )));
        request.put("generationConfig", Map.of(
                "responseMimeType", MediaType.APPLICATION_JSON_VALUE,
                "responseJsonSchema", responseSchema()
        ));
        return request;
    }

    private String systemPrompt() {
        String taskCodes = String.join(", ", allowedTaskCodes());
        return """
                You analyze Korean banking requests from senior users.
                Return only the JSON object required by the supplied schema.
                Correct obvious speech-recognition mistakes without adding facts.
                intent and candidates must use only these task codes: %s.
                candidates must be ordered by likelihood and contain no duplicates.
                Detect only these voice-phishing patterns:
                - IMPERSONATION: someone claims to be a prosecutor, police officer, financial regulator, bank, or other trusted institution. Example: "검찰 수사관입니다".
                - SAFE_ACCOUNT: someone asks the user to move money to a so-called safe or protected account. Example: "안전계좌로 보내세요".
                - SECRECY: someone orders the user not to tell family, bank staff, or anyone else. Example: "가족에게 말하면 안 됩니다".
                - REMOTE_CONTROL: someone asks to install a remote-control app, share the screen, or grant device access. Example: "원격 앱을 설치하세요".
                - URGENCY: someone pressures the user to act immediately by threatening loss, arrest, account suspension, or another penalty. Example: "지금 당장 보내지 않으면 계좌가 정지됩니다".
                fraud_detected must be true if and only if fraud_patterns contains at least one item.
                Each fraud evidence value must copy a non-empty exact phrase from the original user's utterance.
                Do not infer a pattern when the utterance itself does not contain supporting words.
                Do not return duplicate pairs of fraud pattern type and evidence.
                If no allowed task is plausible, use an empty intent and empty candidates.
                Calibrate confidence conservatively. Use high confidence only when one task is explicit.
                Examples:
                - "통장을 잃어버려서 다시 만들고 싶어" -> intent PASSBOOK_REISSUE, high confidence.
                - "매달 빠져나가는 돈을 바꾸고 싶어" -> intent AUTO_TRANSFER_CHANGE, high confidence.
                - "아들 이름으로 뭘 해야 해" -> intent PROXY_TASK with plausible candidates and medium confidence.
                - "그거 있잖아 그거 좀 해줘" -> empty intent, empty candidates, low confidence.
                """.formatted(taskCodes).trim();
    }

    private Map<String, Object> responseSchema() {
        List<String> taskCodes = allowedTaskCodes();
        List<String> fraudPatternTypes = Arrays.stream(FraudPatternType.values())
                .map(Enum::name)
                .toList();
        List<String> intentValues = new ArrayList<>(taskCodes);
        intentValues.add("");
        Map<String, Object> fraudPattern = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "type", Map.of("type", "string", "enum", fraudPatternTypes),
                        "evidence", Map.of("type", "string"),
                        "explanation", Map.of("type", "string")
                ),
                "required", List.of("type", "evidence", "explanation")
        );

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "corrected_text", Map.of("type", "string"),
                        "fraud_detected", Map.of("type", "boolean"),
                        "fraud_patterns", Map.of("type", "array", "items", fraudPattern),
                        "intent", Map.of("type", "string", "enum", intentValues),
                        "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "candidates", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string", "enum", taskCodes),
                                "maxItems", 3
                        )
                ),
                "required", List.of(
                        "corrected_text",
                        "fraud_detected",
                        "fraud_patterns",
                        "intent",
                        "confidence",
                        "candidates"
                )
        );
    }

    private List<String> allowedTaskCodes() {
        return Arrays.stream(TaskTypeCode.values()).map(Enum::name).toList();
    }

    private void validatePayload(LlmPayload payload) {
        if (payload == null
                || payload.correctedText() == null
                || payload.fraudDetected() == null
                || payload.intent() == null
                || payload.confidence() == null
                || payload.confidence().isNaN()
                || payload.confidence().isInfinite()
                || payload.confidence() < 0.0
                || payload.confidence() > 1.0
                || payload.candidates() == null
                || payload.candidates().size() > 3
                || payload.candidates().stream().anyMatch(Objects::isNull)
                || payload.fraudPatterns() == null
                || payload.fraudPatterns().stream().anyMatch(Objects::isNull)) {
            throw new LlmClassificationException("LLM returned malformed structured output");
        }
    }

    private Candidate extractCandidate(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new LlmClassificationException("Gemini response did not include a candidate");
        }
        Candidate candidate = response.candidates().get(0);
        if (!"STOP".equals(candidate.finishReason())) {
            throw new LlmClassificationException("Gemini response did not finish normally");
        }
        return candidate;
    }

    private String extractContent(Candidate candidate) {
        if (candidate.content() == null || candidate.content().parts() == null) {
            throw new LlmClassificationException("Gemini response did not include content");
        }
        String content = candidate.content().parts().stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .reduce("", String::concat);
        if (content.isBlank()) {
            throw new LlmClassificationException("Gemini response did not include content");
        }
        return content;
    }

    private String resolveResponseModel(GenerateContentResponse response) {
        if (response == null || response.modelVersion() == null || response.modelVersion().isBlank()) {
            return properties.getModel();
        }
        return response.modelVersion();
    }

    private int tokenCount(Integer value) {
        return value == null ? 0 : value;
    }

    private void logHttpFailure(
            RestClientResponseException exception,
            Instant startedAt,
            GeminiRequestQuota.QuotaUsage quotaUsage
    ) {
        GeminiHttpError error = parseHttpError(exception.getResponseBodyAsString());
        log.warn(
                "Gemini HTTP request failed: requestedModel={}, promptVersion={}, httpStatus={}, providerStatus={}, providerCode={}, latencyMs={}, minuteRemaining={}, dailyRemaining={}",
                properties.getModel(),
                PROMPT_VERSION,
                exception.getStatusCode().value(),
                error.status(),
                error.code(),
                Duration.between(startedAt, Instant.now()).toMillis(),
                quotaUsage.minuteRemaining(),
                quotaUsage.dailyRemaining()
        );
    }

    private GeminiHttpError parseHttpError(String responseBody) {
        try {
            GeminiHttpErrorResponse response = objectMapper.readValue(responseBody, GeminiHttpErrorResponse.class);
            if (response != null && response.error() != null) {
                return response.error().sanitized();
            }
        } catch (JacksonException ignored) {
            // The raw provider body can contain request data and must never be logged.
        }
        return GeminiHttpError.unknown();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateContentResponse(
            @JsonProperty("modelVersion") String modelVersion,
            List<Candidate> candidates,
            @JsonProperty("usageMetadata") UsageMetadata usageMetadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiHttpErrorResponse(GeminiHttpError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiHttpError(Integer code, String status) {
        private static final String UNKNOWN = "unknown";

        private GeminiHttpError sanitized() {
            return new GeminiHttpError(code, safe(status));
        }

        private static GeminiHttpError unknown() {
            return new GeminiHttpError(null, UNKNOWN);
        }

        private static String safe(String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }
            String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
            return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmPayload(
            @JsonProperty("corrected_text") String correctedText,
            @JsonProperty("fraud_detected") Boolean fraudDetected,
            @JsonProperty("fraud_patterns") List<LlmFraudPattern> fraudPatterns,
            String intent,
            Double confidence,
            List<String> candidates
    ) {
        LlmAnalysisOutput toOutput(String model) {
            return new LlmAnalysisOutput(
                    model,
                    PROMPT_VERSION,
                    correctedText,
                    Boolean.TRUE.equals(fraudDetected),
                    fraudPatterns,
                    intent,
                    confidence,
                    candidates
            );
        }
    }
}
