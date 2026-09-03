package com.piggyback.backend.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-jwt-provider-must-be-long-enough");
        jwtProvider = new JwtProvider(properties);
    }

    @Test
    void 액세스_토큰을_발급하고_userId를_복원한다() {
        String token = jwtProvider.createAccessToken(42L);
        assertThat(jwtProvider.parseAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void 가입_토큰을_발급하고_휴대폰_번호를_복원한다() {
        String token = jwtProvider.createSignupToken("01012345678");
        assertThat(jwtProvider.parseSignupToken(token)).isEqualTo("01012345678");
    }

    @Test
    void 가입_토큰을_액세스_토큰으로_쓰면_UNAUTHORIZED다() {
        String signupToken = jwtProvider.createSignupToken("01012345678");
        assertThatThrownBy(() -> jwtProvider.parseAccessToken(signupToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 위조된_토큰은_UNAUTHORIZED다() {
        assertThatThrownBy(() -> jwtProvider.parseAccessToken("invalid.token.value"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
