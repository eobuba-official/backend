package com.piggyback.backend.speech.dto;

import com.piggyback.backend.speech.domain.TranscriptionSource;

public record SpeechTranscriptionResponse(
        String transcript,
        TranscriptionSource source,
        String browserTranscript,
        Double sttConfidence,
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
