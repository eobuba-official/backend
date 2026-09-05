package com.piggyback.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;
import com.piggyback.backend.domain.consultation.Consultation;
import com.piggyback.backend.domain.consultation.ConsultationRepository;
import com.piggyback.backend.domain.consultation.ConsultationStatus;
import com.piggyback.backend.domain.consultation.InputMethod;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private static final Long USER_ID = 1L;

    private UserRepository userRepository;
    private GuardianRepository guardianRepository;
    private ConsultationRepository consultationRepository;
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        guardianRepository = mock(GuardianRepository.class);
        consultationRepository = mock(ConsultationRepository.class);
        userService = new UserService(userRepository, guardianRepository, consultationRepository);
        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("김시니어");
        when(user.getPhoneNumber()).thenReturn("01012345678");
    }

    private Guardian guardianOf(User owner) {
        Guardian guardian = mock(Guardian.class);
        when(guardian.getUser()).thenReturn(owner);
        return guardian;
    }

    @Test
    void 내_정보와_자녀_목록을_반환한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        Guardian guardian = Guardian.builder()
                .user(user).name("김아들").phoneNumber("01098765432").relation(GuardianRelation.SON)
                .build();
        when(guardianRepository.findAllByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(List.of(guardian));

        var response = userService.getMe(USER_ID);

        assertThat(response.name()).isEqualTo("김시니어");
        assertThat(response.guardians()).hasSize(1);
        assertThat(response.guardians().get(0).relation()).isEqualTo("아들");
    }

    @Test
    void 자녀가_3명이면_추가는_INVALID_STATE다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(guardianRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> userService.addGuardian(USER_ID,
                new GuardianAddRequest("김딸", "01011112222", "딸")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 잘못된_관계_값은_INVALID_INPUT이다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(guardianRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> userService.addGuardian(USER_ID,
                new GuardianAddRequest("김딸", "01011112222", "이웃")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 자녀_추가_성공_시_현재_인원을_반환한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(guardianRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(1L, 2L);
        when(guardianRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.addGuardian(USER_ID, new GuardianAddRequest("김딸", "01011112222", "딸"));

        assertThat(response.guardianCount()).isEqualTo(2);
        assertThat(response.guardian().relation()).isEqualTo("딸");
    }

    @Test
    void 마지막_자녀_삭제_시_fraudAlertDisabled가_true다() {
        Guardian guardian = guardianOf(user);
        when(guardianRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(guardian));
        when(guardianRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(0L);

        var response = userService.deleteGuardian(USER_ID, 10L);

        assertThat(response.fraudAlertDisabled()).isTrue();
        verify(guardian).softDelete();
    }

    @Test
    void 자녀가_남아있으면_fraudAlertDisabled가_false다() {
        Guardian guardian = guardianOf(user);
        when(guardianRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(guardian));
        when(guardianRepository.countByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(2L);

        var response = userService.deleteGuardian(USER_ID, 10L);

        assertThat(response.fraudAlertDisabled()).isFalse();
        assertThat(response.guardianCount()).isEqualTo(2);
    }

    @Test
    void 타인의_자녀_삭제는_NOT_FOUND다() {
        User other = mock(User.class);
        when(other.getId()).thenReturn(99L);
        Guardian guardian = guardianOf(other);
        when(guardianRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(guardian));

        assertThatThrownBy(() -> userService.deleteGuardian(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 존재하지_않는_자녀_삭제는_NOT_FOUND다() {
        when(guardianRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteGuardian(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 상담_이력은_correctedUtterance를_반환하고_원문은_노출하지_않는다() {
        Consultation consultation = Consultation.builder()
                .userId(USER_ID)
                .utterance("통정 잃어버렸어")
                .correctedUtterance("통장을 잃어버렸어요")
                .inputMethod(InputMethod.VOICE)
                .status(ConsultationStatus.TASK_CONFIRMED)
                .taskTypeCode("PASSBOOK_REISSUE")
                .build();
        when(consultationRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(consultation));

        var response = userService.getConsultations(USER_ID);

        assertThat(response.consultations()).hasSize(1);
        var item = response.consultations().get(0);
        assertThat(item.correctedUtterance()).isEqualTo("통장을 잃어버렸어요");
        assertThat(item.status()).isEqualTo("TASK_CONFIRMED");
        assertThat(item).hasNoNullFieldsOrPropertiesExcept("confidence");
    }
}

