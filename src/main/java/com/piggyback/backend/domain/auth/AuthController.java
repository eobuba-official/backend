package com.piggyback.backend.domain.auth;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "휴대전화 SMS 인증과 회원가입을 처리합니다.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sms/request")
    @Operation(summary = "SMS 인증번호 요청", description = "입력한 휴대전화 번호로 인증번호를 발송합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 발송 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "휴대전화 번호 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "인증번호 요청 횟수 초과")
    })
    public ApiResponse<SmsRequestResponse> requestSmsCode(@RequestBody @Valid SmsRequestRequest request) {
        return ApiResponse.success(authService.requestSmsCode(request.phoneNumber()));
    }

    @PostMapping("/sms/verify")
    @Operation(summary = "SMS 인증번호 확인", description = "발송된 인증번호를 확인하고 회원가입에 사용할 인증 토큰을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 확인 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 정보 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증번호 불일치, 만료 또는 인증 시도 횟수 초과")
    })
    public ApiResponse<SmsVerifyResponse> verifySmsCode(@RequestBody @Valid SmsVerifyRequest request) {
        return ApiResponse.success(authService.verifySmsCode(request.phoneNumber(), request.code()));
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "SMS 인증을 완료한 휴대전화 번호와 사용자 정보로 계정을 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "회원가입 정보 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 휴대전화 번호")
    })
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }
}
