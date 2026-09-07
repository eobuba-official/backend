package com.piggyback.backend.classification.infrastructure.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@Getter
@Setter
@ConfigurationProperties(prefix = "piggyback.integrations.gemini")
public class GeminiProperties {

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String apiKey = "";
    private String model = "gemini-3.5-flash-lite";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int maxRetries = 1;
    private Duration retryDelay = Duration.ofSeconds(1);
    private int requestsPerMinute = 15;
    private int requestsPerDay = 500;
    private String quotaZone = "America/Los_Angeles";

    public String generateContentUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmClassificationException("Gemini base URL is not configured");
        }
        if (model == null || model.isBlank()) {
            throw new LlmClassificationException("Gemini model is not configured");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBaseUrl + "/models/" + model + ":generateContent";
    }

    public ZoneId quotaZoneId() {
        try {
            return ZoneId.of(quotaZone);
        } catch (RuntimeException exception) {
            throw new LlmClassificationException("Gemini quota zone is invalid", exception);
        }
    }

    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmClassificationException("Gemini API key is not configured");
        }
        generateContentUrl();
        if (requestsPerMinute < 1 || requestsPerDay < 1) {
            throw new LlmClassificationException("Gemini request quota must be positive");
        }
        if (maxRetries < 0 || retryDelay == null || retryDelay.isNegative()) {
            throw new LlmClassificationException("Gemini retry configuration is invalid");
        }
        quotaZoneId();
    }
}
