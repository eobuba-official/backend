package com.piggyback.backend.domain.notification;

import com.piggyback.backend.domain.user.Guardian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 가족 등록 안내 Mock SMS 이력. 사기 경고 알림(guardian_notification)은 상담에 종속되지만
// 등록 안내는 상담 없이 발송되므로 별도 테이블로 분리한다.
@Getter
@Entity
@Table(name = "guardian_enrollment_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianEnrollmentNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false)
    private Guardian guardian;

    // 템플릿 + 이름(최대 50자×2) + 거부 URL + 토큰(36자) 합산 여유분. 300이면 긴 이름·운영 도메인에서 넘칠 수 있다.
    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Builder
    private GuardianEnrollmentNotice(Guardian guardian, String message) {
        this.guardian = guardian;
        this.message = message;
        this.sentAt = LocalDateTime.now();
    }
}
