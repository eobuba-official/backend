package com.piggyback.backend.domain.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsVerificationRepository extends JpaRepository<SmsVerification, Long> {

    Optional<SmsVerification> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
