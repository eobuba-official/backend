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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class OpenAiCompatibleTaskClassificationClient implements TaskClassificationClient {

    static final String PROMPT_VERSION = "task-classification-v1.2";

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
            return payload.toOutput(model);
        } catch (JacksonException exception) {
            throw new LlmClassificationException("LLM returned malformed structured output", exception);
        } catch (RestClientException exception) {
            throw new LlmClassificationException("LLM request failed", exception);
        }
    }

    private Map<String, Object> buildRequest(String model, String utterance) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", 0);
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

    private String systemPrompt() {
        String taskCodes = String.join(", ", allowedTaskCodes());
        return """
                You analyze Korean banking requests from senior users.
                Return only the JSON object required by the supplied schema.
                Correct obvious speech-recognition mistakes without adding facts.
                intent and candidates must use only these task codes: %s.
                candidates must be ordered by likelihood and contain no duplicates.
                Fraud evidence must be an exact phrase found in the user's utterance.
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
    record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
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
