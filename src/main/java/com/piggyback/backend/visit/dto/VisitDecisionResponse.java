package com.piggyback.backend.visit.dto;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.domain.VisitDecision;
import com.piggyback.backend.visit.domain.OfficialChannel;
import com.piggyback.backend.visit.domain.RemoteMethod;
import java.util.List;

public record VisitDecisionResponse(
        TaskTypeCode taskTypeCode,
        String taskTypeName,
        VisitDecision decision,
        String reason,
        List<RemoteMethod> remoteMethods,
        List<OfficialChannel> officialChannels
) {
    public VisitDecisionResponse {
        remoteMethods = remoteMethods == null ? List.of() : List.copyOf(remoteMethods);
        officialChannels = officialChannels == null ? List.of() : List.copyOf(officialChannels);
    }
}
