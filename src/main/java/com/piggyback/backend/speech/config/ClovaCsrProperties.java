package com.piggyback.backend.speech.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "piggyback.integrations.clova-csr")
public class ClovaCsrProperties {

    private String endpoint = "https://naveropenapi.apigw.ntruss.com/recog/v1/stt";
    private String apiKeyId = "";
    private String apiKey = "";
    private String language = "Kor";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(65);
    private DataSize maxAudioSize = DataSize.ofMegabytes(3);

    public boolean hasCredentials() {
        return apiKeyId != null && !apiKeyId.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
