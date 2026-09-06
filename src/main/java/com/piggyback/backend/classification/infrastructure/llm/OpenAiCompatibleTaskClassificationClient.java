package com.piggyback.backend.classification.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.classification.port.LlmFraudPattern;
import com.piggyback.backend.classification.port.TaskClassificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class OpenAiCompatibleTaskClassificationClient implements TaskClassificationClient {

    static final String PROMPT_VERSION = "task-classification-v1.3-fraud-guardrail";

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleTaskClassificationClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    @Autowired
    public OpenAiCompatibleTaskClassificationClient(
            ObjectMapper objectMapper,
            LlmProperties properties
    ) {
        this(buildRestClient(RestClient.builder(), properties), objectMapper, properties);
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, LlmProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return restClientBuilder.requestFactory(requestFactory).build();
    }

    OpenAiCompatibleTaskClassificationClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            LlmProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public LlmAnalysisOutput analyze(String utterance) {
        LlmClassificationException primaryFailure;
        try {
            return invoke(properties.getPrimaryModel(), utterance);
        } catch (LlmClassificationException exception) {
            primaryFailure = exception;
            log.warn(
                    "Primary LLM classification failed: model={}, promptVersion={}",
                    properties.getPrimaryModel(),
                    PROMPT_VERSION
            );
        }

        if (properties.getFallbackModel() == null
                || properties.getFallbackModel().isBlank()
                || properties.getFallbackModel().equals(properties.getPrimaryModel())) {
            throw primaryFailure;
        }

        try {
            return invoke(properties.getFallbackModel(), utterance);
        } catch (LlmClassificationException fallbackFailure) {
            fallbackFailure.addSuppressed(primaryFailure);
            throw fallbackFailure;
        }
    }

    private LlmAnalysisOutput invoke(String model, String utterance) {
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(properties.chatCompletionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuthorization(headers, properties))
                    .body(buildRequest(model, utterance))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String content = extractContent(response);
            LlmPayload payload = objectMapper.readValue(content, LlmPayload.class);
            validatePayload(payload);
            String responseModel = resolveResponseModel(response, model);
            log.info(
                    "LLM classification response received: requestedModel={}, responseModel={}, promptVersion={}",
                    model,
                    responseModel,
                    PROMPT_VERSION
            );
            return payload.toOutput(responseModel);
        } catch (JacksonException exception) {
            throw new LlmClassificationException("LLM returned malformed structured output", exception);
        } catch (RestClientResponseException exception) {
            logHttpFailure(model, exception);
            throw new LlmClassificationException("LLM request failed", exception);
        } catch (RestClientException exception) {
            log.warn(
                    "LLM transport request failed: requestedModel={}, promptVersion={}, cause={}",
                    model,
                    PROMPT_VERSION,
                    exception.getClass().getSimpleName()
            );
            throw new LlmClassificationException("LLM request failed", exception);
        }
    }

    private Map<String, Object> buildRequest(String model, String utterance) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        applyModelOptions(request, model);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", utterance)
        ));
        request.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "piggyback_analysis",
                        "strict", true,
                        "schema", responseSchema()
                )
        ));
        return request;
    }

    private void applyModelOptions(Map<String, Object> request, String model) {
        if (!usesDefaultTemperatureOnly(model)) {
            request.put("temperature", 0);
        }
    }

    private boolean usesDefaultTemperatureOnly(String model) {
        if (model == null) {
            return false;
        }
        String normalizedModel = model.trim().toLowerCase(Locale.ROOT);
        return normalizedModel.equals("gpt-5-nano")
                || normalizedModel.startsWith("gpt-5-nano-");
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
        if (payload.correctedText() == null
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

    private void applyAuthorization(HttpHeaders headers, LlmProperties llmProperties) {
        if (llmProperties.getApiKey() != null && !llmProperties.getApiKey().isBlank()) {
            headers.set(
                    HttpHeaders.AUTHORIZATION,
                    llmProperties.getAuthScheme() + " " + llmProperties.getApiKey()
            );
        }
    }

    private String resolveResponseModel(ChatCompletionResponse response, String requestedModel) {
        if (response.model() == null || response.model().isBlank()) {
            return requestedModel;
        }
        return response.model();
    }

    private void logHttpFailure(String requestedModel, RestClientResponseException exception) {
        LlmHttpError error = parseHttpError(exception.getResponseBodyAsString());
        log.warn(
                "LLM HTTP request failed: requestedModel={}, promptVersion={}, status={}, errorType={}, errorCode={}, errorParam={}",
                requestedModel,
                PROMPT_VERSION,
                exception.getStatusCode().value(),
                error.type(),
                error.code(),
                error.param()
        );
    }

    private LlmHttpError parseHttpError(String responseBody) {
        try {
            LlmHttpErrorResponse response = objectMapper.readValue(responseBody, LlmHttpErrorResponse.class);
            if (response != null && response.error() != null) {
                return response.error().sanitized();
            }
        } catch (JacksonException ignored) {
            // Never log the raw provider response because it may contain sensitive request data.
        }
        return LlmHttpError.unknown();
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new LlmClassificationException("LLM response did not include message content");
        }
        return response.choices().get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(String model, List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmHttpErrorResponse(LlmHttpError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmHttpError(String type, String code, String param) {
        private static final String UNKNOWN = "unknown";

        private LlmHttpError sanitized() {
            return new LlmHttpError(safe(type), safe(code), safe(param));
        }

        private static LlmHttpError unknown() {
            return new LlmHttpError(UNKNOWN, UNKNOWN, UNKNOWN);
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
