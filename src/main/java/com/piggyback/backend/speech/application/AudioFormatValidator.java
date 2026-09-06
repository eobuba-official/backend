package com.piggyback.backend.speech.application;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AudioFormatValidator {

    private static final Map<String, AudioFormat> SUPPORTED_MEDIA_TYPES = Map.ofEntries(
            Map.entry("audio/mpeg", AudioFormat.MP3),
            Map.entry("audio/mp3", AudioFormat.MP3),
            Map.entry("audio/aac", AudioFormat.AAC),
            Map.entry("audio/x-aac", AudioFormat.AAC),
            Map.entry("audio/ac3", AudioFormat.AC3),
            Map.entry("audio/x-ac3", AudioFormat.AC3),
            Map.entry("audio/ogg", AudioFormat.OGG),
            Map.entry("application/ogg", AudioFormat.OGG),
            Map.entry("audio/flac", AudioFormat.FLAC),
            Map.entry("audio/x-flac", AudioFormat.FLAC),
            Map.entry("audio/wav", AudioFormat.WAV),
            Map.entry("audio/wave", AudioFormat.WAV),
            Map.entry("audio/x-wav", AudioFormat.WAV),
            Map.entry("audio/vnd.wave", AudioFormat.WAV)
    );

    public void validate(String contentType, byte[] audio) {
        AudioFormat expectedFormat = SUPPORTED_MEDIA_TYPES.get(normalizeContentType(contentType));
        if (expectedFormat == null || !expectedFormat.matches(audio)) {
            throw new BusinessException(ErrorCode.INVALID_AUDIO);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private enum AudioFormat {
        MP3 {
            @Override
            boolean matches(byte[] bytes) {
                return startsWith(bytes, "ID3")
                        || bytes.length >= 2
                        && unsigned(bytes[0]) == 0xFF
                        && (unsigned(bytes[1]) & 0xE0) == 0xE0;
            }
        },
        AAC {
            @Override
            boolean matches(byte[] bytes) {
                return startsWith(bytes, "ADIF")
                        || bytes.length >= 2
                        && unsigned(bytes[0]) == 0xFF
                        && (unsigned(bytes[1]) & 0xF6) == 0xF0;
            }
        },
        AC3 {
            @Override
            boolean matches(byte[] bytes) {
                return bytes.length >= 2
                        && unsigned(bytes[0]) == 0x0B
                        && unsigned(bytes[1]) == 0x77;
            }
        },
        OGG {
            @Override
            boolean matches(byte[] bytes) {
                return startsWith(bytes, "OggS");
            }
        },
        FLAC {
            @Override
            boolean matches(byte[] bytes) {
                return startsWith(bytes, "fLaC");
            }
        },
        WAV {
            @Override
            boolean matches(byte[] bytes) {
                return bytes.length >= 12
                        && startsWith(bytes, 0, "RIFF")
                        && startsWith(bytes, 8, "WAVE");
            }
        };

        abstract boolean matches(byte[] bytes);

        static boolean startsWith(byte[] bytes, String signature) {
            return startsWith(bytes, 0, signature);
        }

        static boolean startsWith(byte[] bytes, int offset, String signature) {
            byte[] signatureBytes = signature.getBytes(StandardCharsets.US_ASCII);
            return bytes.length >= offset + signatureBytes.length
                    && Arrays.equals(bytes, offset, offset + signatureBytes.length,
                    signatureBytes, 0, signatureBytes.length);
        }

        static int unsigned(byte value) {
            return value & 0xFF;
        }
    }
}
