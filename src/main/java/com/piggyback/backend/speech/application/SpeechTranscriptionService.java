package com.piggyback.backend.speech.application;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.speech.config.ClovaCsrProperties;
import com.piggyback.backend.speech.dto.SpeechTranscriptionResponse;
import com.piggyback.backend.speech.infrastructure.clova.SpeechRecognitionException;
import com.piggyback.backend.speech.port.SpeechRecognitionClient;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeechTranscriptionService {

    private static final int MAX_BROWSER_TRANSCRIPT_LENGTH = 1_000;

    private final SpeechRecognitionClient speechRecognitionClient;
    private final AudioFormatValidator audioFormatValidator;
    private final ClovaCsrProperties properties;

    public SpeechTranscriptionService(
            SpeechRecognitionClient speechRecognitionClient,
            AudioFormatValidator audioFormatValidator,
            ClovaCsrProperties properties
    ) {
        this.speechRecognitionClient = speechRecognitionClient;
        this.audioFormatValidator = audioFormatValidator;
        this.properties = properties;
    }

    public SpeechTranscriptionResponse transcribe(MultipartFile audio, String browserTranscript) {
        String normalizedBrowserTranscript = normalizeBrowserTranscript(browserTranscript);
        validateFile(audio);

        byte[] audioBytes = readAudio(audio);
        try {
            audioFormatValidator.validate(audio.getContentType(), audioBytes);
            try {
                String transcript = speechRecognitionClient.transcribe(audioBytes);
                return SpeechTranscriptionResponse.clova(transcript, normalizedBrowserTranscript);
            } catch (SpeechRecognitionException exception) {
                if (normalizedBrowserTranscript != null) {
                    return SpeechTranscriptionResponse.webSpeechFallback(normalizedBrowserTranscript);
                }
                throw exception;
            }
        } finally {
            Arrays.fill(audioBytes, (byte) 0);
        }
    }

    private void validateFile(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_AUDIO, "음성 파일은 필수입니다.");
        }
        if (audio.getSize() > properties.getMaxAudioSize().toBytes()) {
            throw new BusinessException(ErrorCode.AUDIO_TOO_LARGE);
        }
    }

    private byte[] readAudio(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_AUDIO, "음성 파일을 읽을 수 없습니다.");
        }
    }

    private String normalizeBrowserTranscript(String browserTranscript) {
        if (browserTranscript == null || browserTranscript.isBlank()) {
            return null;
        }
        String normalized = browserTranscript.trim();
        if (normalized.length() > MAX_BROWSER_TRANSCRIPT_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "browserTranscript는 1,000자를 초과할 수 없습니다."
            );
        }
        return normalized;
    }
}
