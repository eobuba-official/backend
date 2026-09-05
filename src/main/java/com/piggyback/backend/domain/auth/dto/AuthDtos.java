package com.piggyback.backend.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SmsRequestRequest(
            @NotBlank(message = "휴대폰 번호는 필수입니다.")
            @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
            String phoneNumber
    ) {
    }

    public record SmsRequestResponse(int expiresInSeconds, String mockCode) {
    }

    public record SmsVerifyRequest(
            @NotBlank(message = "휴대폰 번호는 필수입니다.")
            @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
            String phoneNumber,
            @NotBlank(message = "인증번호는 필수입니다.")
            String code
    ) {
    }

    public record SmsVerifyResponse(boolean registered, String accessToken, String signupToken) {

        public static SmsVerifyResponse existingUser(String accessToken) {
            return new SmsVerifyResponse(true, accessToken, null);
        }

        public static SmsVerifyResponse newUser(String signupToken) {
            return new SmsVerifyResponse(false, null, signupToken);
        }
    }

    public record GuardianRequest(
            @NotBlank(message = "자녀 이름은 필수입니다.")
            @Size(max = 50)
            String name,
            @NotBlank(message = "자녀 휴대폰 번호는 필수입니다.")
            @Pattern(regexp = "^01\\d{8,9}$", message = "자녀 휴대폰 번호 형식이 올바르지 않습니다.")
            String phoneNumber,
            @NotBlank(message = "관계는 필수입니다.")
            @Pattern(regexp = "^(아들|딸|배우자|기타)$", message = "관계는 아들, 딸, 배우자, 기타 중 하나여야 합니다.")
            String relation
    ) {
    }

    public record SignupRequest(
            @NotBlank(message = "signupToken은 필수입니다.")
            String signupToken,
            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50)
            String name,
            @Valid
            @NotEmpty(message = "자녀는 1명 이상 등록해야 합니다.")
            @Size(min = 1, max = 3, message = "자녀는 1~3명까지 등록할 수 있습니다.")
            List<GuardianRequest> guardians
    ) {
    }

    public record SignupResponse(Long userId, String accessToken) {
    }
}
