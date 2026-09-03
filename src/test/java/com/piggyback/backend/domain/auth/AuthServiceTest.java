package com.piggyback.backend.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.auth.AuthProperties;
import com.piggyback.backend.common.auth.JwtProvider;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.auth.dto.AuthDtos.GuardianRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyResponse;
import com.piggyback.backend.domain.user.GuardianRepository;
import com.piggyback.backend.domain.user.User;
import com.piggyback.backend.domain.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final String PHONE = "01012345678";

    private SmsVerificationRepository smsVerificationRepository;
    private UserRepository userRepository;
    private GuardianRepository guardianRepository;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        smsVerificationRepository = mock(SmsVerificationRepository.class);
        userRepository = mock(UserRepository.class);
        guardianRepository = mock(GuardianRepository.class);
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-auth-service-must-be-long-enough");
        properties.setExposeMockCode(true);
        jwtProvider = new JwtProvider(properties);
        authService = new AuthService(smsVerificationRepository, userRepository, guardianRepository,
                jwtProvider, properties);
    }

    private SmsVerification savedVerification(String code, LocalDateTime expiresAt) {
        return SmsVerification.builder()
                .phoneNumber(PHONE).code(code).purpose(SmsPurpose.LOGIN).expiresAt(expiresAt)
                .build();
    }

    @Test
    void 인증번호_발송_시_6자리_코드와_TTL을_반환한다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.empty());
        when(userRepository.existsByPhoneNumber(PHONE)).thenReturn(false);
        when(smsVerificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SmsRequestResponse response = authService.requestSmsCode(PHONE);

        assertThat(response.expiresInSeconds()).isEqualTo(180);
        assertThat(response.mockCode()).matches("\\d{6}");
    }

    @Test
    void 쿨다운_내_재발송_요청은_SMS_REQUEST_COOLDOWN이다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(savedVerification("123456", LocalDateTime.now().plusMinutes(3))));

        assertThatThrownBy(() -> authService.requestSmsCode(PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SMS_REQUEST_COOLDOWN);
    }

    @Test
    void 이미_검증에_사용된_인증번호는_재사용할_수_없다() {
        SmsVerification verification = savedVerification("123456", LocalDateTime.now().plusMinutes(3));
        verification.markVerified();
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SMS_CODE);
    }

    @Test
    void 검증_5회_실패_후에는_올바른_코드도_거부된다() {
        SmsVerification verification = savedVerification("123456", LocalDateTime.now().plusMinutes(3));
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(verification));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "999999"))
                    .isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SMS_CODE);
    }

    @Test
    void 기존_회원_인증_성공_시_accessToken을_발급한다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(savedVerification("123456", LocalDateTime.now().plusMinutes(3))));
        User user = User.builder().phoneNumber(PHONE).name("김시니어").build();
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));

        SmsVerifyResponse response = authService.verifySmsCode(PHONE, "123456");

        assertThat(response.registered()).isTrue();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.signupToken()).isNull();
    }

    @Test
    void 신규_사용자_인증_성공_시_signupToken을_발급한다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(savedVerification("123456", LocalDateTime.now().plusMinutes(3))));
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        SmsVerifyResponse response = authService.verifySmsCode(PHONE, "123456");

        assertThat(response.registered()).isFalse();
        assertThat(jwtProvider.parseSignupToken(response.signupToken())).isEqualTo(PHONE);
    }

    @Test
    void 인증번호가_틀리면_INVALID_SMS_CODE다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(savedVerification("123456", LocalDateTime.now().plusMinutes(3))));

        assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SMS_CODE);
    }

    @Test
    void 만료된_인증번호는_INVALID_SMS_CODE다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.of(savedVerification("123456", LocalDateTime.now().minusSeconds(1))));

        assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SMS_CODE);
    }

    @Test
    void 발송_이력이_없으면_INVALID_SMS_CODE다() {
        when(smsVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifySmsCode(PHONE, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SMS_CODE);
    }

    @Test
    void 가입_성공_시_userId와_accessToken을_반환하고_자녀를_저장한다() {
        String signupToken = jwtProvider.createSignupToken(PHONE);
        when(userRepository.existsByPhoneNumber(PHONE)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(guardianRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.signup(new SignupRequest(signupToken, "김시니어",
                List.of(new GuardianRequest("김아들", "01098765432", "아들"))));

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void 이미_가입된_번호로_가입하면_ALREADY_REGISTERED다() {
        String signupToken = jwtProvider.createSignupToken(PHONE);
        when(userRepository.existsByPhoneNumber(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest(signupToken, "김시니어",
                List.of(new GuardianRequest("김아들", "01098765432", "아들")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    void 위조된_signupToken은_INVALID_INPUT이다() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("bad.token", "김시니어",
                List.of(new GuardianRequest("김아들", "01098765432", "아들")))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
