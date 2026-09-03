package com.piggyback.backend.entity;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "task_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskType {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 40, nullable = false)
    private TaskTypeCode code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "easy_description", length = 300, nullable = false)
    private String easyDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visit_decision", length = 20, nullable = false)
    private VisitDecision defaultVisitDecision;

    public TaskType(
            TaskTypeCode code,
            String name,
            String easyDescription,
            VisitDecision defaultVisitDecision
    ) {
        this.code = code;
        this.name = name;
        this.easyDescription = easyDescription;
        this.defaultVisitDecision = defaultVisitDecision;
    }
}
