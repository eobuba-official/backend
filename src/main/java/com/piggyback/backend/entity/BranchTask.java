package com.piggyback.backend.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "branch_task")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTask {

    @EmbeddedId
    private BranchTaskId id;

    @MapsId("branchId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    public BranchTask(Branch branch, BranchTaskId id) {
        this.branch = branch;
        this.id = id;
    }
}
