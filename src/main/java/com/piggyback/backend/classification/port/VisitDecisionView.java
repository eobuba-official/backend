package com.piggyback.backend.classification.port;

import com.piggyback.backend.visit.dto.VisitDecisionResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

public record VisitDecisionView(
        @Schema(
                description = "방문 필요 여부",
                allowableValues = {"NO_VISIT", "CHECK_NEEDED", "VISIT_REQUIRED"},
                example = "VISIT_REQUIRED"
        )
        String decision,
        @Schema(description = "방문 판단 이유")
        String reason,
        @Schema(description = "비대면 처리 방법")
        List<RemoteMethod> remoteMethods,
        @Schema(description = "확인이 필요한 공식 채널")
        List<OfficialChannel> officialChannels
) {
    private static final Set<String> ALLOWED_DECISIONS = Set.of(
            "NO_VISIT",
            "CHECK_NEEDED",
            "VISIT_REQUIRED"
    );

    public VisitDecisionView {
        if (!ALLOWED_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("Unsupported visit decision: " + decision);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Visit decision reason is required");
        }
        remoteMethods = remoteMethods == null ? List.of() : List.copyOf(remoteMethods);
        officialChannels = officialChannels == null ? List.of() : List.copyOf(officialChannels);
    }

    public static VisitDecisionView from(VisitDecisionResponse response) {
        return new VisitDecisionView(
                response.decision().name(),
                response.reason(),
                response.remoteMethods().stream()
                        .map(item -> new RemoteMethod(
                                item.channel(),
                                item.description(),
                                item.easyDescription()
                        ))
                        .toList(),
                response.officialChannels().stream()
                        .map(item -> new OfficialChannel(
                                item.name(),
                                item.phone(),
                                item.description()
                        ))
                        .toList()
        );
    }

    public record RemoteMethod(String channel, String description, String easyDescription) {
    }

    public record OfficialChannel(String name, String phone, String description) {
    }
}
