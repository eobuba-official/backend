package com.piggyback.backend.domain.user.dto;

import com.piggyback.backend.domain.consultation.Consultation;
import com.piggyback.backend.domain.user.Guardian;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    public record GuardianResponse(Long guardianId, String name, String phoneNumber, String relation) {

        public static GuardianResponse from(Guardian guardian) {
            return new GuardianResponse(guardian.getId(), guardian.getName(),
                    guardian.getPhoneNumber(), guardian.getRelation().getLabel());
        }
    }

    public record MeResponse(Long userId, String name, String phoneNumber, List<GuardianResponse> guardians) {
    }

    public record GuardianAddRequest(
            @NotBlank(message = "자녀 이름은 필수입니다.")
            @Size(max = 50)
            String name,
            @NotBlank(message = "자녀 휴대폰 번호는 필수입니다.")
            @Pattern(regexp = "^01\\d{8,9}$", message = "자녀 휴대폰 번호 형식이 올바르지 않습니다.")
            String phoneNumber,
            @NotBlank(message = "관계는 필수입니다.")
            String relation
    ) {
    }

    public record GuardianAddResponse(GuardianResponse guardian, int guardianCount) {
    }

    public record GuardianDeleteResponse(int guardianCount, boolean fraudAlertDisabled) {
    }

    public record ConsultationHistoryItem(String consultationId, String correctedUtterance, String status,
                                          String taskTypeCode, BigDecimal confidence, LocalDateTime createdAt) {

        public static ConsultationHistoryItem from(Consultation consultation) {
            return new ConsultationHistoryItem(consultation.getId(), consultation.getCorrectedUtterance(),
                    consultation.getStatus().name(), consultation.getTaskTypeCode(),
                    consultation.getConfidence(), consultation.getCreatedAt());
        }
    }

    public record ConsultationHistoryResponse(List<ConsultationHistoryItem> consultations) {
    }
}
