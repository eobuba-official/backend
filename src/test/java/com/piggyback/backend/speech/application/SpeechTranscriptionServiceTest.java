package com.piggyback.backend.speech.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.speech.config.ClovaCsrProperties;
import com.piggyback.backend.speech.domain.TranscriptionSource;
import com.piggyback.backend.speech.infrastructure.clova.SpeechRecognitionException;
import com.piggyback.backend.speech.port.SpeechRecognitionClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class SpeechTranscriptionServiceTest {

    private SpeechRecognitionClient client;
    private ClovaCsrProperties properties;
    private SpeechTranscriptionService service;

    @BeforeEach
    void setUp() {
        client = org.mockito.Mockito.mock(SpeechRecognitionClient.class);
        properties = new ClovaCsrProperties();
        properties.setMaxAudioSize(DataSize.ofMegabytes(3));
        service = new SpeechTranscriptionService(client, new AudioFormatValidator(), properties);
    }

    @Test
    void returnsClovaTranscriptionAndRequiresUserRecheck() {
        MockMultipartFile audio = validWav();
        when(client.transcribe(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenReturn("통장을 다시 만들고 싶어요");

        var response = service.transcribe(audio, "통장을 다시 만들고 시퍼");

        assertThat(response.transcript()).isEqualTo("통장을 다시 만들고 싶어요");
        assertThat(response.source()).isEqualTo(TranscriptionSource.CLOVA_CSR);
        assertThat(response.browserTranscript()).isEqualTo("통장을 다시 만들고 시퍼");
        assertThat(response.sttConfidence()).isNull();
        assertThat(response.recheckNeeded()).isTrue();
    }

    @Test
    void fallsBackToBrowserTranscriptWhenClovaFails() {
        when(client.transcribe(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenThrow(new SpeechRecognitionException(ErrorCode.STT_ERROR, "provider failed"));

        var response = service.transcribe(validWav(), "  통장을 다시 만들고 싶어  ");

        assertThat(response.transcript()).isEqualTo("통장을 다시 만들고 싶어");
        assertThat(response.source()).isEqualTo(TranscriptionSource.WEB_SPEECH_FALLBACK);
        assertThat(response.recheckNeeded()).isTrue();
    }

    @Test
    void propagatesClovaErrorWhenBrowserTranscriptIsMissing() {
        when(client.transcribe(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenThrow(new SpeechRecognitionException(ErrorCode.STT_ERROR, "provider failed"));

        assertThatThrownBy(() -> service.transcribe(validWav(), null))
                .isInstanceOfSatisfying(SpeechRecognitionException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_ERROR));
    }

    @Test
    void rejectsMissingOrEmptyAudio() {
        assertThatThrownBy(() -> service.transcribe(null, "브라우저 문장"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUDIO));

        MockMultipartFile empty = new MockMultipartFile("audio", "empty.wav", "audio/wav", new byte[0]);
        assertThatThrownBy(() -> service.transcribe(empty, "브라우저 문장"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUDIO));

        verify(client, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void rejectsUnsupportedMediaTypeEvenWhenBrowserTranscriptExists() {
        MockMultipartFile webm = new MockMultipartFile(
                "audio",
                "speech.webm",
                "audio/webm",
                "not-a-clova-format".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.transcribe(webm, "브라우저 문장"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUDIO));
        verify(client, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void rejectsContentWhoseSignatureDoesNotMatchMediaType() {
        MockMultipartFile corrupted = new MockMultipartFile(
                "audio",
                "speech.wav",
                "audio/wav",
                "RIFF-but-not-wave".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> service.transcribe(corrupted, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_AUDIO));
    }

    @Test
    void rejectsAudioLargerThanConfiguredLimit() {
        properties.setMaxAudioSize(DataSize.ofBytes(11));
        MockMultipartFile audio = validWav();

        assertThatThrownBy(() -> service.transcribe(audio, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUDIO_TOO_LARGE));
        verify(client, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void rejectsBrowserTranscriptLongerThanOneThousandCharacters() {
        assertThatThrownBy(() -> service.transcribe(validWav(), "가".repeat(1_001)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(client, never()).transcribe(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    private MockMultipartFile validWav() {
        byte[] bytes = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return new MockMultipartFile("audio", "speech.wav", "audio/wav", bytes);
    }
}
