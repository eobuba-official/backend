package com.piggyback.backend.entity;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.domain.OfficialChannel;
import com.piggyback.backend.visit.domain.RemoteMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "task_visit_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskVisitRule {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type_code", length = 40, nullable = false)
    private TaskTypeCode taskTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20, nullable = false)
    private VisitDecision decision;

    @Column(name = "reason", length = 300, nullable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remote_methods", columnDefinition = "json")
    private List<RemoteMethod> remoteMethods;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "official_channels", columnDefinition = "json")
    private List<OfficialChannel> officialChannels;

    public TaskVisitRule(
            TaskTypeCode taskTypeCode,
            VisitDecision decision,
            String reason,
            List<RemoteMethod> remoteMethods,
            List<OfficialChannel> officialChannels
    ) {
        this.taskTypeCode = taskTypeCode;
        this.decision = decision;
        this.reason = reason;
        this.remoteMethods = copyOf(remoteMethods);
        this.officialChannels = copyOf(officialChannels);
    }

    private static <T> List<T> copyOf(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
