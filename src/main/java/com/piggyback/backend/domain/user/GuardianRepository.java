package com.piggyback.backend.domain.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findAllByUserIdAndDeletedAtIsNull(Long userId);

    long countByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Guardian> findByIdAndDeletedAtIsNull(Long id);
}
