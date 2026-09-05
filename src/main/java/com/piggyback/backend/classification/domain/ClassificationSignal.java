package com.piggyback.backend.classification.domain;

import java.util.List;

public record ClassificationSignal(
        String correctedUtterance,
        String intent,
        Double confidence,
        List<String> candidates
) {
    public ClassificationSignal {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
