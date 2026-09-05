package com.piggyback.backend.domain.user;

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
        this.createdAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
