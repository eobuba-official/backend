package com.piggyback.backend.domain.notification;

import com.piggyback.backend.domain.user.Guardian;
import com.piggyback.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuardianEnrollmentNotifier {

    private final GuardianEnrollmentNoticeRepository noticeRepository;
    private final GuardianProperties guardianProperties;

    // Mock SMS: 실제 발송 없이 발송 이력만 저장하고 메시지를 반환한다.
    public String notifyEnrollment(User user, Guardian guardian) {
        String message = "[어부바] %s님이 %s님을 보호 가족(%s)으로 등록했어요. 수상한 전화가 감지되면 알림 문자를 보내드려요. 본인이 아니거나 알림을 원치 않으시면 링크에서 거부할 수 있어요. %s?token=%s"
                .formatted(user.getName(), guardian.getName(), guardian.getRelation().getLabel(),
                        guardianProperties.getDeclineBaseUrl(), guardian.getDeclineToken());
        noticeRepository.save(GuardianEnrollmentNotice.builder()
                .guardian(guardian)
                .message(message)
                .build());
        return message;
    }
}
