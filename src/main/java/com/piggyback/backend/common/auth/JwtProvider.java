package com.piggyback.backend.common.auth;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_SIGNUP = "signup";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration signupTokenTtl;

    public JwtProvider(AuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofHours(properties.getAccessTokenTtlHours());
        this.signupTokenTtl = Duration.ofMinutes(properties.getSignupTokenTtlMinutes());
    }

    public String createAccessToken(Long userId) {
        return createToken(TYPE_ACCESS, String.valueOf(userId), accessTokenTtl);
    }

    public String createSignupToken(String phoneNumber) {
        return createToken(TYPE_SIGNUP, phoneNumber, signupTokenTtl);
    }

    public Long parseAccessToken(String token) {
        Claims claims = parse(token, TYPE_ACCESS, ErrorCode.UNAUTHORIZED);
        return Long.valueOf(claims.getSubject());
    }

    public String parseSignupToken(String token) {
        Claims claims = parse(token, TYPE_SIGNUP, ErrorCode.INVALID_INPUT);
        return claims.getSubject();
    }

    private String createToken(String type, String subject, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, String expectedType, ErrorCode onFailure) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get("type", String.class))) {
                throw new BusinessException(onFailure, "토큰 유형이 올바르지 않습니다.");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(onFailure, "유효하지 않거나 만료된 토큰입니다.");
        }
    }
}
