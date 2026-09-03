package com.piggyback.backend.common.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "abuba.auth")
public class AuthProperties {

    private int smsCodeTtlSeconds = 180;
    private int accessTokenTtlHours = 24;
    private int signupTokenTtlMinutes = 10;
    private String jwtSecret;
    private boolean exposeMockCode = false;
}
