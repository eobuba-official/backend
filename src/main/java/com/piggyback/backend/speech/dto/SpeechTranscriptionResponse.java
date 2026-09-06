package com.piggyback.backend.speech.dto;

import com.piggyback.backend.speech.domain.TranscriptionSource;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpeechTranscriptionResponse(
        @Schema(description = "사용자에게 최종 확인을 요청할 인식 문장", example = "통장을 잃어버렸는데 다시 만들고 싶어")
        String transcript,
        @Schema(description = "최종 문장의 출처", allowableValues = {"CLOVA_CSR", "WEB_SPEECH_FALLBACK"}, example = "CLOVA_CSR")
        TranscriptionSource source,
        @Schema(description = "프론트에서 함께 전달한 Web Speech 임시 문장", nullable = true)
        String browserTranscript,
        @Schema(description = "현재 CLOVA CSR은 confidence를 제공하지 않아 null", nullable = true)
        Double sttConfidence,
        @Schema(description = "음성 인식 문장을 사용자에게 다시 확인해야 하는지 여부", example = "true")
        boolean recheckNeeded
) {
    public static SpeechTranscriptionResponse clova(String transcript, String browserTranscript) {
        return new SpeechTranscriptionResponse(
                transcript,
                TranscriptionSource.CLOVA_CSR,
                browserTranscript,
                null,
                true
        );
    }

    public static SpeechTranscriptionResponse webSpeechFallback(String browserTranscript) {
        return new SpeechTranscriptionResponse(
                browserTranscript,
                TranscriptionSource.WEB_SPEECH_FALLBACK,
                browserTranscript,
                null,
                true
        );
    }
}
