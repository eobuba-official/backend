package com.piggyback.backend.domain.auth;

import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sms/request")
    public ApiResponse<SmsRequestResponse> requestSmsCode(@RequestBody @Valid SmsRequestRequest request) {
        return ApiResponse.success(authService.requestSmsCode(request.phoneNumber()));
    }

    @PostMapping("/sms/verify")
    public ApiResponse<SmsVerifyResponse> verifySmsCode(@RequestBody @Valid SmsVerifyRequest request) {
        return ApiResponse.success(authService.verifySmsCode(request.phoneNumber(), request.code()));
    }

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody @Valid SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }
}
