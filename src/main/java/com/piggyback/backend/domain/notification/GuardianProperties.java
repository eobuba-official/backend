package com.piggyback.backend.domain.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "abuba.guardian")
public class GuardianProperties {

    // 자녀가 앱 설치 없이 여는 수신 거부 웹 페이지 주소 (프론트 라우트)
    private String declineBaseUrl = "http://localhost:5173/guardian-decline";
}
