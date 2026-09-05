package com.piggyback.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "recommendation",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_RECOMMENDATION_RANK",
                columnNames = {"consultation_id", "display_rank"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", nullable = false, length = 36)
    private String consultationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "display_rank", nullable = false)
    private int displayRank;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    @Column(name = "expected_wait_minutes", nullable = false)
    private int expectedWaitMinutes;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal score;

    @Column(nullable = false, length = 300)
    private String sentence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Recommendation(
            String consultationId,
            Branch branch,
            int displayRank,
            LocalDate visitDate,
            String timeSlot,
            int expectedWaitMinutes,
            BigDecimal score,
            String sentence
    ) {
        this.consultationId = consultationId;
        this.branch = branch;
        this.displayRank = displayRank;
        this.visitDate = visitDate;
        this.timeSlot = timeSlot;
        this.expectedWaitMinutes = expectedWaitMinutes;
        this.score = score;
        this.sentence = sentence;
        this.createdAt = LocalDateTime.now();
    }
}
