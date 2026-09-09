package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeclineInfoResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeclineRequest;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeclineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 자녀가 문자 링크로 여는 공개 엔드포인트 — 앱 설치·로그인 없이 토큰만으로 접근한다 (JwtAuthFilter 예외).
@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
@Tag(name = "보호자 수신 거부", description = "가족 등록 안내 문자의 링크로 접근하는 공개 API입니다. 인증이 필요하지 않습니다.")
public class GuardianDeclineController {

    private final UserService userService;

    @GetMapping("/decline-info")
    @Operation(summary = "수신 거부 페이지 정보 조회", description = "거부 토큰으로 등록자·보호자 정보와 현재 상태를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "토큰에 해당하는 등록 정보 없음")
    })
    public ApiResponse<GuardianDeclineInfoResponse> getDeclineInfo(
            @Parameter(description = "등록 안내 문자에 포함된 거부 토큰", required = true)
            @RequestParam @NotBlank String token) {
        return ApiResponse.success(userService.getDeclineInfo(token));
    }

    @PostMapping("/decline")
    @Operation(summary = "알림 수신 거부", description = "보호자 등록을 거부 처리합니다. 거부된 보호자에게는 사기 경고 알림을 발송하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거부 처리 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "토큰에 해당하는 등록 정보 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 거부 처리된 등록")
    })
    public ApiResponse<GuardianDeclineResponse> decline(@RequestBody @Valid GuardianDeclineRequest request) {
        return ApiResponse.success(userService.declineGuardian(request.token()));
    }
}
