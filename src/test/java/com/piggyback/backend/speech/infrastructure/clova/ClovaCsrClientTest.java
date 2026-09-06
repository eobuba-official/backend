package com.piggyback.backend.speech.infrastructure.clova;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.speech.config.ClovaCsrProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ClovaCsrClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private ClovaCsrProperties properties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        properties = new ClovaCsrProperties();
        properties.setEndpoint("https://naver.example/recog/v1/stt");
        properties.setApiKeyId("test-client-id");
        properties.setApiKey("test-client-secret");
        properties.setLanguage("Kor");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
    }

    @Test
    void sendsBinaryAudioWithCredentialsAndLanguage() {
        byte[] audio = {1, 2, 3, 4};
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(ClovaCsrClient.API_KEY_ID_HEADER, "test-client-id"))
                .andExpect(header(ClovaCsrClient.API_KEY_HEADER, "test-client-secret"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(audio))
                .andRespond(withSuccess("{\"text\":\"통장을 다시 만들고 싶어요\"}", MediaType.APPLICATION_JSON));

        String transcript = createClient().transcribe(audio);

        assertThat(transcript).isEqualTo("통장을 다시 만들고 싶어요");
        server.verify();
    }

    @Test
    void mapsInvalidAudioResponseToInvalidAudio() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(withBadRequest()
                        .body("{\"error\":{\"errorCode\":\"STT006\",\"message\":\"pre-processing failed\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUDIO));
        server.verify();
    }

    @Test
    void mapsUnauthorizedProviderResponseToSttError() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    @Test
    void mapsProviderPayloadLimitToAudioTooLarge() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.CONTENT_TOO_LARGE));

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUDIO_TOO_LARGE));
        server.verify();
    }

    @Test
    void mapsProviderServerErrorToSttError() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    @Test
    void mapsTimeoutToSttError() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(request -> {
                    throw new ResourceAccessException("read timed out");
                });

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    @Test
    void rejectsMalformedProviderResponse() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    @Test
    void rejectsEmptyTranscription() {
        server.expect(once(), requestTo("https://naver.example/recog/v1/stt?lang=Kor"))
                .andRespond(withSuccess("{\"text\":\"  \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    @Test
    void failsWithoutCallingProviderWhenCredentialsAreMissing() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> createClient().transcribe(new byte[]{1}))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
        server.verify();
    }

    private ClovaCsrClient createClient() {
        return new ClovaCsrClient(restClientBuilder.build(), new ObjectMapper(), properties);
    }
}
