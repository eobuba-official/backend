package com.piggyback.backend.speech.infrastructure.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.speech.config.ClovaCsrProperties;
import com.piggyback.backend.speech.port.SpeechRecognitionClient;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClovaCsrClient implements SpeechRecognitionClient {

    static final String API_KEY_ID_HEADER = "x-ncp-apigw-api-key-id";
    static final String API_KEY_HEADER = "x-ncp-apigw-api-key";

    private static final Logger log = LoggerFactory.getLogger(ClovaCsrClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ClovaCsrProperties properties;

    @Autowired
    public ClovaCsrClient(ObjectMapper objectMapper, ClovaCsrProperties properties) {
        this(buildRestClient(properties), objectMapper, properties);
    }

    ClovaCsrClient(RestClient restClient, ObjectMapper objectMapper, ClovaCsrProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    private static RestClient buildRestClient(ClovaCsrProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String transcribe(byte[] audio) {
        if (!properties.hasCredentials()) {
            throw new SpeechRecognitionException(
                    ErrorCode.STT_ERROR,
                    "CLOVA CSR credentials are not configured"
            );
        }

        try {
            ClovaCsrResponse response = restClient.post()
                    .uri(UriComponentsBuilder.fromUriString(properties.getEndpoint())
                            .queryParam("lang", properties.getLanguage())
                            .build()
                            .toUri())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(API_KEY_ID_HEADER, properties.getApiKeyId())
                    .header(API_KEY_HEADER, properties.getApiKey())
                    .body(audio)
                    .retrieve()
                    .body(ClovaCsrResponse.class);

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new SpeechRecognitionException(
                        ErrorCode.STT_ERROR,
                        "CLOVA CSR returned an empty transcription"
                );
            }
            log.info("CLOVA CSR transcription completed: provider=clova-csr");
            return response.text().trim();
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (RestClientException exception) {
            log.warn(
                    "CLOVA CSR transport failed: provider=clova-csr, cause={}",
                    exception.getClass().getSimpleName()
            );
            throw new SpeechRecognitionException(
                    ErrorCode.STT_ERROR,
                    "CLOVA CSR request failed",
                    exception
            );
        }
    }

    private SpeechRecognitionException mapHttpError(RestClientResponseException exception) {
        ClovaError error = parseError(exception.getResponseBodyAsString());
        ErrorCode errorCode = exception.getStatusCode().value() == 413
                ? ErrorCode.AUDIO_TOO_LARGE
                : isInvalidAudio(error.errorCode()) ? ErrorCode.INVALID_AUDIO : ErrorCode.STT_ERROR;
        log.warn(
                "CLOVA CSR HTTP request failed: provider=clova-csr, status={}, errorCode={}",
                exception.getStatusCode().value(),
                safeLogValue(error.errorCode())
        );
        return new SpeechRecognitionException(errorCode, "CLOVA CSR request failed", exception);
    }

    private boolean isInvalidAudio(String errorCode) {
        if (errorCode == null) {
            return false;
        }
        return switch (errorCode.toUpperCase(Locale.ROOT)) {
            case "STT002", "STT003", "STT006", "STT007" -> true;
            default -> false;
        };
    }

    private ClovaError parseError(String responseBody) {
        try {
            ClovaErrorResponse response = objectMapper.readValue(responseBody, ClovaErrorResponse.class);
            if (response != null && response.error() != null) {
                return response.error();
            }
        } catch (JacksonException ignored) {
            // Never log the provider response body because it may contain sensitive data.
        }
        return new ClovaError("unknown");
    }

    private String safeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.length() <= 40 ? sanitized : sanitized.substring(0, 40);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClovaCsrResponse(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClovaErrorResponse(ClovaError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClovaError(String errorCode) {
    }
}
