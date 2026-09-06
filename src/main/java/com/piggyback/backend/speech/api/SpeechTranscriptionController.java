package com.piggyback.backend.speech.api;

import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.speech.application.SpeechTranscriptionService;
import com.piggyback.backend.speech.dto.SpeechTranscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/speech")
@RequiredArgsConstructor
public class SpeechTranscriptionController {

    private final SpeechTranscriptionService speechTranscriptionService;

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SpeechTranscriptionResponse> transcribe(
            @RequestAttribute(value = JwtAuthFilter.USER_ID_ATTRIBUTE, required = false) Long userId,
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "browserTranscript", required = false) String browserTranscript
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(speechTranscriptionService.transcribe(audio, browserTranscript));
    }
}
