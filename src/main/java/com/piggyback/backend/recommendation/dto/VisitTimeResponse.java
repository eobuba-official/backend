package com.piggyback.backend.recommendation.dto;

import java.time.LocalDate;

public record VisitTimeResponse(
        LocalDate date,
        String dayLabel,
        String timeSlot,
        String timeLabel
) {
}
