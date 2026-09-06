package com.piggyback.backend.speech.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "piggyback.integrations.clova-csr")
public class ClovaCsrProperties {

    private String endpoint = "https://naveropenapi.apigw.ntruss.com/recog/v1/stt";
    private String apiKeyId = "";
    private String apiKey = "";
    private String language = "Kor";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(65);
    private DataSize maxAudioSize = DataSize.ofMegabytes(3);

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(String apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public DataSize getMaxAudioSize() {
        return maxAudioSize;
    }

    public void setMaxAudioSize(DataSize maxAudioSize) {
        this.maxAudioSize = maxAudioSize;
    }

    public boolean hasCredentials() {
        return apiKeyId != null && !apiKeyId.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
