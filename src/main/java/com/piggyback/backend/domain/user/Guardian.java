package com.piggyback.backend.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "guardian")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, length = 20)
    private GuardianRelation relation;

    // 기존 로컬 DB(ddl-auto=update)에 컬럼 추가 시 기존 행이 DEFAULT로 채워지도록 명시
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'")
    private GuardianStatus status;

    // 수신 거부 링크용 토큰. 컬럼 추가 이전에 생성된 행은 NULL (거부 링크 미발급)
    @Column(name = "decline_token", length = 36, unique = true)
    private String declineToken;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // guardian_notification이 guardian.id를 FK로 참조하므로 하드 삭제 대신 소프트 삭제한다.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Guardian(User user, String name, String phoneNumber, GuardianRelation relation) {
        this.user = user;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relation = relation;
        this.status = GuardianStatus.ACTIVE;
        this.declineToken = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void decline() {
        this.status = GuardianStatus.DECLINED;
        this.declinedAt = LocalDateTime.now();
    }
}
