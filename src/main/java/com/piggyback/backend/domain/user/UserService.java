package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.consultation.ConsultationRepository;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryItem;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddRequest;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddResponse;
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

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = findUser(userId);
        List<GuardianResponse> guardians = guardianRepository.findAllByUserId(userId).stream()
                .map(GuardianResponse::from)
                .toList();
        return new MeResponse(user.getId(), user.getName(), user.getPhoneNumber(), guardians);
    }

    @Transactional
    public GuardianAddResponse addGuardian(Long userId, GuardianAddRequest request) {
        User user = findUser(userId);
        if (guardianRepository.countByUserId(userId) >= MAX_GUARDIANS) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "자녀는 최대 3명까지 등록할 수 있습니다.");
        }
        Guardian guardian = guardianRepository.save(Guardian.builder()
                .user(user)
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .relation(GuardianRelation.fromLabel(request.relation()))
                .build());
        int count = (int) guardianRepository.countByUserId(userId);
        return new GuardianAddResponse(GuardianResponse.from(guardian), count);
    }

    @Transactional
    public GuardianDeleteResponse deleteGuardian(Long userId, Long guardianId) {
        Guardian guardian = guardianRepository.findById(guardianId)
                .filter(g -> g.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "자녀를 찾을 수 없습니다."));
        guardianRepository.delete(guardian);
        int count = (int) guardianRepository.countByUserId(userId);
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
