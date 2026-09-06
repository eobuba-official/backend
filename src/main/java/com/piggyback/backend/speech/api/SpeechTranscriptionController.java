package com.piggyback.backend.speech.api;

import com.piggyback.backend.common.auth.JwtAuthFilter;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.speech.application.SpeechTranscriptionService;
import com.piggyback.backend.speech.dto.SpeechTranscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "음성 인식", description = "업로드한 음성을 CLOVA CSR로 인식하고 Web Speech 문장을 폴백으로 사용합니다.")
@SecurityRequirement(name = "bearerAuth")
public class SpeechTranscriptionController {

    private final SpeechTranscriptionService speechTranscriptionService;

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "음성 파일 인식",
            description = """
                    최대 3MB의 MP3, AAC, AC3, OGG, FLAC, WAV 파일을 CLOVA CSR로 인식합니다.
                    browserTranscript를 함께 보내면 CLOVA 장애 시 Web Speech 결과로 폴백합니다.
                    반환된 transcript를 사용자에게 확인받은 뒤 /api/v1/analyze에 inputMethod=VOICE로 전달하세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CLOVA 또는 Web Speech 폴백 인식 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "빈 파일, 미지원 형식 또는 손상된 음성"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "3MB 파일 크기 제한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "CLOVA 오류이며 사용할 Web Speech 폴백도 없음")
    })
    public ApiResponse<SpeechTranscriptionResponse> transcribe(
            @Parameter(hidden = true)
            @RequestAttribute(value = JwtAuthFilter.USER_ID_ATTRIBUTE, required = false) Long userId,
            @Parameter(
                    description = "CLOVA CSR 지원 형식의 원본 음성 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @Parameter(
                    description = "브라우저 Web Speech API가 인식한 임시 문장. CLOVA 실패 시에만 최종 폴백으로 사용합니다.",
                    example = "통장을 잃어버렸는데 다시 만들고 싶어"
            )
            @RequestParam(value = "browserTranscript", required = false) String browserTranscript
    ) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(speechTranscriptionService.transcribe(audio, browserTranscript));
    }
}
