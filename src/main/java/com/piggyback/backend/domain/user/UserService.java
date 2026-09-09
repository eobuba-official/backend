package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.auth.AuthProperties;
import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.consultation.ConsultationRepository;
import com.piggyback.backend.domain.notification.GuardianEnrollmentNotifier;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryItem;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddRequest;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeclineInfoResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeclineResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeleteResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.MeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_GUARDIANS = 3;

    private final UserRepository userRepository;
    private final GuardianRepository guardianRepository;
    private final ConsultationRepository consultationRepository;
    private final GuardianEnrollmentNotifier enrollmentNotifier;
    private final AuthProperties authProperties;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = findUser(userId);
        List<GuardianResponse> guardians = guardianRepository.findAllByUserIdAndDeletedAtIsNull(userId).stream()
                .map(GuardianResponse::from)
                .toList();
        return new MeResponse(user.getId(), user.getName(), user.getPhoneNumber(), guardians);
    }

    @Transactional
    public GuardianAddResponse addGuardian(Long userId, GuardianAddRequest request) {
        User user = findUser(userId);
        if (guardianRepository.countByUserIdAndDeletedAtIsNull(userId) >= MAX_GUARDIANS) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "자녀는 최대 3명까지 등록할 수 있습니다.");
        }
        Guardian guardian = guardianRepository.save(Guardian.builder()
                .user(user)
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .relation(GuardianRelation.fromLabel(request.relation()))
                .build());
        String message = enrollmentNotifier.notifyEnrollment(user, guardian);
        int count = (int) guardianRepository.countByUserIdAndDeletedAtIsNull(userId);
        String mockNotification = authProperties.isExposeMockCode() ? message : null;
        return new GuardianAddResponse(GuardianResponse.from(guardian), count, mockNotification);
    }

    @Transactional(readOnly = true)
    public GuardianDeclineInfoResponse getDeclineInfo(String token) {
        return GuardianDeclineInfoResponse.from(findGuardianByDeclineToken(token));
    }

    @Transactional
    public GuardianDeclineResponse declineGuardian(String token) {
        Guardian guardian = findGuardianByDeclineToken(token);
        if (guardian.getStatus() == GuardianStatus.DECLINED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 알림 수신을 거부한 등록입니다.");
        }
        guardian.decline();
        return new GuardianDeclineResponse(guardian.getStatus().name());
    }

    private Guardian findGuardianByDeclineToken(String token) {
        return guardianRepository.findByDeclineTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "가족 등록 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public GuardianDeleteResponse deleteGuardian(Long userId, Long guardianId) {
        Guardian guardian = guardianRepository.findByIdAndDeletedAtIsNull(guardianId)
                .filter(g -> g.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "자녀를 찾을 수 없습니다."));
        guardian.softDelete();
        int count = (int) guardianRepository.countByUserIdAndDeletedAtIsNull(userId);
        return new GuardianDeleteResponse(count, count == 0);
    }

    @Transactional(readOnly = true)
    public ConsultationHistoryResponse getConsultations(Long userId) {
        List<ConsultationHistoryItem> items = consultationRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ConsultationHistoryItem::from)
                .toList();
        return new ConsultationHistoryResponse(items);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}

