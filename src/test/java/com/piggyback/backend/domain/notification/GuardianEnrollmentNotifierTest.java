package com.piggyback.backend.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.domain.user.Guardian;
import com.piggyback.backend.domain.user.GuardianRelation;
import com.piggyback.backend.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuardianEnrollmentNotifierTest {

    private GuardianEnrollmentNoticeRepository noticeRepository;
    private GuardianEnrollmentNotifier notifier;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(GuardianEnrollmentNoticeRepository.class);
        GuardianProperties properties = new GuardianProperties();
        properties.setDeclineBaseUrl("https://abuba.example/decline");
        notifier = new GuardianEnrollmentNotifier(noticeRepository, properties);
    }

    @Test
    void 등록_안내_메시지에_이름_관계_거부_링크가_포함된다() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("김시니어");
        Guardian guardian = Guardian.builder()
                .user(user).name("김아들").phoneNumber("01098765432").relation(GuardianRelation.SON)
                .build();

        String message = notifier.notifyEnrollment(user, guardian);

        assertThat(message).contains("김시니어", "김아들", "아들")
                .contains("https://abuba.example/decline?token=" + guardian.getDeclineToken());
        verify(noticeRepository).save(any(GuardianEnrollmentNotice.class));
    }

    @Test
    void 이름이_최대_길이여도_메시지가_컬럼_한도_500자를_넘지_않는다() {
        String longName = "가".repeat(50);
        User user = mock(User.class);
        when(user.getName()).thenReturn(longName);
        Guardian guardian = Guardian.builder()
                .user(user).name(longName).phoneNumber("01098765432").relation(GuardianRelation.SPOUSE)
                .build();

        String message = notifier.notifyEnrollment(user, guardian);

        assertThat(message.length()).isLessThanOrEqualTo(500);
    }
}
