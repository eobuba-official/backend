package com.piggyback.backend.domain.auth;

import com.piggyback.backend.common.auth.AuthProperties;
import com.piggyback.backend.common.auth.JwtProvider;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupRequest;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SignupResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsRequestResponse;
import com.piggyback.backend.domain.auth.dto.AuthDtos.SmsVerifyResponse;
import com.piggyback.backend.domain.user.Guardian;
import com.piggyback.backend.domain.user.GuardianRepository;
import com.piggyback.backend.domain.user.User;
import com.piggyback.backend.domain.user.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsVerificationRepository smsVerificationRepository;
    private final UserRepository userRepository;
    private final GuardianRepository guardianRepository;
    private final JwtProvider jwtProvider;
    private final AuthProperties authProperties;

    @Transactional
    public SmsRequestResponse requestSmsCode(String phoneNumber) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        SmsPurpose purpose = userRepository.existsByPhoneNumber(phoneNumber) ? SmsPurpose.LOGIN : SmsPurpose.SIGNUP;
        smsVerificationRepository.save(SmsVerification.builder()
                .phoneNumber(phoneNumber)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusSeconds(authProperties.getSmsCodeTtlSeconds()))
                .build());
        String mockCode = authProperties.isExposeMockCode() ? code : null;
        return new SmsRequestResponse(authProperties.getSmsCodeTtlSeconds(), mockCode);
    }

    @Transactional
    public SmsVerifyResponse verifySmsCode(String phoneNumber, String code) {
        SmsVerification verification = smsVerificationRepository
                .findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SMS_CODE));
        if (verification.isExpired(LocalDateTime.now()) || !verification.getCode().equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_SMS_CODE);
        }
        verification.markVerified();
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(user -> SmsVerifyResponse.existingUser(jwtProvider.createAccessToken(user.getId())))
                .orElseGet(() -> SmsVerifyResponse.newUser(jwtProvider.createSignupToken(phoneNumber)));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String phoneNumber = jwtProvider.parseSignupToken(request.signupToken());
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        User user = userRepository.save(User.builder()
                .phoneNumber(phoneNumber)
                .name(request.name())
                .build());
        request.guardians().forEach(g -> guardianRepository.save(Guardian.builder()
                .user(user)
                .name(g.name())
                .phoneNumber(g.phoneNumber())
                .relation(g.relation())
                .build()));
        return new SignupResponse(user.getId(), jwtProvider.createAccessToken(user.getId()));
    }
}
