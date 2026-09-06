package com.piggyback.backend.speech.port;

public interface SpeechRecognitionClient {

    String transcribe(byte[] audio);
}
