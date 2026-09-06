package com.piggyback.backend.classification.infrastructure.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "piggyback.integrations.llm")
public class LlmProperties {

    private String baseUrl = "";
    private String apiKey = "";
    private String chatCompletionsPath = "/chat/completions";
    private String authScheme = "Bearer";
    private String primaryModel = "gpt-5-nano";
    private String fallbackModel = "gpt-4o-mini";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(15);

    public String chatCompletionsUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmClassificationException("LLM base URL is not configured");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        String normalizedPath = chatCompletionsPath.startsWith("/")
                ? chatCompletionsPath
                : "/" + chatCompletionsPath;
        return normalizedBaseUrl + normalizedPath;
    }
}
