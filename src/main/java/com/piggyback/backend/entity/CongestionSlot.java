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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "congestion_slot",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CONGESTION_SLOT",
                columnNames = {"branch_id", "day_of_week", "time_slot"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CongestionSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    @Column(name = "expected_wait_minutes", nullable = false)
    private int expectedWaitMinutes;

    public CongestionSlot(Branch branch, int dayOfWeek, String timeSlot, int expectedWaitMinutes) {
        this.branch = branch;
        this.dayOfWeek = dayOfWeek;
        this.timeSlot = timeSlot;
        this.expectedWaitMinutes = expectedWaitMinutes;
    }
}
